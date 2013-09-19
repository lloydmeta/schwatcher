package com.beachape.filemanagement

import akka.actor.{Actor, Props}
import akka.agent.Agent
import akka.routing.SmallestMailboxRouter
import akka.util.Timeout
import com.beachape.filemanagement.Messages._
import com.beachape.filemanagement.RegistryTypes._
import com.typesafe.scalalogging.slf4j.Logging
import java.nio.file.StandardWatchEventKinds._
import java.nio.file._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.existentials
import scala.language.postfixOps

/**
 * Companion object for creating Monitor actor instances
 */
object MonitorActor {

  /**
   * Factory method for the params required to instantiate a MonitorActor
   *
   * @param concurrency Integer, the number of concurrent threads for handling callbacks
   * @return Props for instantiating a MonitorActor
   */
  def apply(concurrency: Int = 5) = {
    require(concurrency > 1, s"Callback concurrency requested is $concurrency but it should at least be 1")
    Props(classOf[MonitorActor], concurrency)
  }
}

/**
 * Actor for registering feedbacks and delegating callback execution
 *
 * Should be instantiated with Props provided via companion object factory
 * method
 */
class MonitorActor(concurrency: Int = 5) extends Actor with Logging with RecursiveFileActions {

  implicit val timeout = Timeout(10 seconds)
  implicit val system = context.system
  implicit val ec = context.dispatcher

  // Smallest mailbox router for callback actors
  val callbackActors = context.actorOf(
    CallbackActor().withRouter(SmallestMailboxRouter(concurrency)),
    "callbackActors")

  // Use Akka Agent to help keep things atomic and thread-safe
  private val eventTypeCallbackRegistryMap = Map(
    ENTRY_CREATE -> Agent(CallbackRegistry(ENTRY_CREATE)),
    ENTRY_MODIFY -> Agent(CallbackRegistry(ENTRY_MODIFY)),
    ENTRY_DELETE -> Agent(CallbackRegistry(ENTRY_DELETE))
  )

  val watchServiceTask = new WatchServiceTask(self)
  val watchThread = new Thread(watchServiceTask, "WatchService")

  override def preStart() {
    watchThread.setDaemon(true)
    watchThread.start()
  }

  override def postStop() {
    watchThread.interrupt()
  }

  def receive = {
    case message: EventAtPath => {
      // Ensure that only absolute paths are used
      val (event, path) = (message.event, message.path.toAbsolutePath)
      logger.info(s"Event $event at path: $path")
      processCallbacksForEventPath(event.asInstanceOf[WatchEvent.Kind[Path]], path)()
      // If event is ENTRY_DELETE or the path is a file, check for callbacks that need
      // to be fired for the directory the file is in
      if (event == ENTRY_DELETE || path.toFile.isFile)
        processCallbacksForEventPath(event.asInstanceOf[WatchEvent.Kind[Path]], path.getParent)(path)
    }
    case message: RegisterCallback => {
      // Ensure that only absolute paths are used
      val absolutePath = message.path.toAbsolutePath
      if (message.recursive) {
        recursivelyAddPathCallback(message.event, absolutePath, message.callback)
        recursivelyAddPathToWatchServiceTask(message.event, absolutePath)
      } else {
        addPathCallback(message.event, absolutePath, message.callback)
        addPathToWatchServiceTask(message.event, absolutePath)
      }
    }
    case message: UnRegisterCallback => {
      // Ensure that only absolute paths are used
      val absolutePath = message.path.toAbsolutePath
      if (message.recursive)
        recursivelyRemoveCallbacksForPath(message.event, absolutePath)
      else
        removeCallbacksForPath(message.event, absolutePath)
    }
    case _ => logger.error("Monitor Actor received an unexpected message :( !")
  }

  /**
   * Registers a callback for a path for an Event type
   *
   * If the path does not exist at the time of adding, a log message is created and the
   * path is ignored
   *
   * @param eventType WatchEvent.Kind[Path] Java7 Event type
   * @param path Path (Java type) to be registered
   * @param callback Callback function, Path => Init
   * @return Path used for registration
   */
  def addPathCallback(eventType: WatchEvent.Kind[Path], path: Path, callback: Callback): Path = {
    eventTypeCallbackRegistryMap(eventType) send (_ withPathCallback(path,callback))
    path
  }

  /**
   * Recursively registers a callback for a path for an Event type.
   * Only recursively registers callbacks for paths that are directories including the current path.
   *
   * If the path is not that of a directory, register the callback only for the path itself
   * then move on. Only recursively registers callbacks for paths that are directories including the current path.
   *
   * @param eventType WatchEvent.Kind[Path] Java7 Event type
   * @param path Path (Java type) to be registered
   * @param callback Callback function, Path => Init
   * @return Path used for registration
   */
  def recursivelyAddPathCallback(eventType: WatchEvent.Kind[Path], path: Path, callback: Callback): Path = {
    eventTypeCallbackRegistryMap(eventType) send (_ withPathCallbackRecursive(path, callback))
    path
  }

  /**
   * Removes the callbacks for a specific path, does not care if Path had no callbacks in the
   * first place. Only recursively un-registers callbacks for paths that are directories under the current path.
   *
   * Note that this does not remove the event listeners from the Java API,
   * because such functionality does not exist. All this does is make sure that the callbacks
   * registered to a specific path do not get fired. Depending on your use case,
   * it may make more sense to just kill the monitor actor to start fresh.
   *
   * @param eventType WatchEvent.Kind[Path] Java7 Event type
   * @param path Path (Java type) to be registered
   * @return Path used for un-registering callbacks
   */
  def removeCallbacksForPath(eventType: WatchEvent.Kind[Path], path: Path): Path = {
    eventTypeCallbackRegistryMap(eventType) send (_ withoutCallbacksForPath path)
    path
  }

  /**
   * Recursively removes the callbacks for a specific path, does not care if Path had no callbacks in the
   * first place. Only recursively un-registers callbacks for paths that are directories under the current path.
   *
   * Note that this does not remove the event listeners from the Java API,
   * because such functionality does not exist. All this does is make sure that the callbacks
   * registered to a specific path do not get fired. Depending on your use case,
   * it may make more sense to just kill the monitor actor to start fresh.
   *
   * @param eventType WatchEvent.Kind[Path] Java7 Event type
   * @param path Path (Java type) to be registered
   * @return Path used for un-registering callbacks
   */
  def recursivelyRemoveCallbacksForPath(eventType: WatchEvent.Kind[Path], path: Path): Path = {
    eventTypeCallbackRegistryMap(eventType) send (_ withoutCallbacksForPathRecursive path)
    path
  }

  /**
   * Retrieves the callbacks registered for a path for an Event type
   *
   * Note that #await is called on the agent so that the thread blocks until all changes
   * have been made on the agent.
   *
   * @param eventType WatchEvent.Kind[Path] Java7 Event type
   * @param path Path (Java type) to be registered
   * @return Option[Callbacks] for the path at the event type (Option[List[Path => Unit]])
   */
  def callbacksForPath(eventType: WatchEvent.Kind[Path], path: Path): Option[Callbacks] = {
    eventTypeCallbackRegistryMap.get(eventType) flatMap { registryAgent =>
      Await.result(registryAgent.future, 10 seconds).callbacksForPath(path) }
  }

  /**
   * Adds a path to be monitored by the Watch Service Task
   *
   * @param eventType WatchEvent.Kind[Path] Java7 Event type
   * @param path Path (Java type) to be registered
   */
  def addPathToWatchServiceTask(eventType: WatchEvent.Kind[Path], path: Path) {
    logger.debug("Adding path to WatchServiceTask")
    watchServiceTask.watch(path, eventType)
  }

  /**
   * Recursively adds a path to be monitored by the Watch Service Task
   *
   * @param eventType WatchEvent.Kind[Path] Java7 Event type
   * @param path Path (Java type) to be registered
   */
  def recursivelyAddPathToWatchServiceTask(eventType: WatchEvent.Kind[Path], path: Path) {
    addPathToWatchServiceTask(eventType, path)
    forEachDir(path) {
      (directory, attributes) =>
        addPathToWatchServiceTask(eventType, directory)
    }
  }

  /**
   * Finds the callbacks for a given EventType and path and sends them all to
   * the CallbackActor pool to get processed
   *
   * @param event WatchEvent.Kind[Path] Java7 Event type
   * @param lookupPath Path (Java type) to be registered
   * @param causerPath Path (Java type) to be sent to the callback, defaults to lookupPath
   * @return Unit
   */
  def processCallbacksForEventPath(event: WatchEvent.Kind[Path], lookupPath: Path)(causerPath: Path = lookupPath) {
    for {
      callbacks <- callbacksForPath(event, lookupPath)
      callback <- callbacks
    } {
      logger.debug(s"Sending callback for path: $causerPath")
      callbackActors ! PerformCallback(causerPath, callback)
    }
  }
}
