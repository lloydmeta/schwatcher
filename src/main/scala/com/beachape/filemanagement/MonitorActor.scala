package com.beachape.filemanagement

import akka.actor.{ActorLogging, Actor, Props}
import akka.routing.{SmallestMailboxRouter, DefaultResizer}
import com.beachape.filemanagement.Messages._
import com.beachape.filemanagement.RegistryTypes.Callbacks
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{Path, WatchEvent}
import java.nio.file.WatchEvent.Modifier

/**
 * Companion object for creating Monitor actor instances
 */
object MonitorActor {

  type CallbackRegistryMap = Map[WatchEvent.Kind[Path], CallbackRegistry]
  /**
   * Factory method for the params required to instantiate a MonitorActor
   *
   * @param concurrency Integer, the number of concurrent threads for handling callbacks
   * @return Props for instantiating a MonitorActor
   */
  def apply(concurrency: Int = 5) = {
    require(concurrency >= 1, s"Callback concurrency requested is $concurrency but it should at least be 1")
    Props(classOf[MonitorActor], concurrency)
  }

  val initialEventTypeCallbackRegistryMap = Map(
    ENTRY_CREATE -> CallbackRegistry(),
    ENTRY_MODIFY -> CallbackRegistry(),
    ENTRY_DELETE -> CallbackRegistry())
}

/**
 * Actor for registering callbacks and delegating callback execution
 *
 * Should be instantiated with Props provided via companion object factory
 * method
 */
class MonitorActor(concurrency: Int = 5) extends Actor with ActorLogging with RecursiveFileActions {

  import com.beachape.filemanagement.MonitorActor.CallbackRegistryMap

  // Smallest mailbox router for callback actors
  private[this] val callbackActors = context.actorOf(
    CallbackActor().withRouter(
      SmallestMailboxRouter(
        resizer = Some(DefaultResizer(lowerBound = concurrency, upperBound = concurrency + 1)))),
      "callbackActors")

  private[this] val monitorActor = self
  private[this] val watchServiceTask = new WatchServiceTask(monitorActor)
  private[this] val watchThread = new Thread(watchServiceTask, "WatchService")

  override def preStart() = {
    watchThread.setDaemon(true)
    watchThread.start()
  }

  override def postStop() = {
    watchThread.interrupt()
  }

  def receive = withCallbackRegistryMap(MonitorActor.initialEventTypeCallbackRegistryMap)

  /**
   * Returns a new Receive that uses a given CallbackRegistryMap
   * @param currentCallbackRegistryMap
   * @return
   */
  def withCallbackRegistryMap(currentCallbackRegistryMap: CallbackRegistryMap): Receive = {
    case EventAtPath(event, path) => {
      log.debug(s"Event $event at path: $path")
      // Ensure that only absolute paths are used
      val absolutePath = path.toAbsolutePath
      processCallbacksFor(currentCallbackRegistryMap, event.asInstanceOf[WatchEvent.Kind[Path]], absolutePath)
    }

    case RegisterCallback(event, modifier, recursive, path, callback) => {
      // Ensure that only absolute paths are used
      val absolutePath = path.toAbsolutePath
      addPathToWatchServiceTask(event, modifier, absolutePath, recursive)
      context.become(
        withCallbackRegistryMap(
          newCallbackRegistryMap(
            currentCallbackRegistryMap,
            event,
            _ withCallbackFor(absolutePath, callback, recursive)
          )
        )
      )
    }

    case UnRegisterCallback(event, recursive, path) => {
      // Ensure that only absolute paths are used
      val absolutePath = path.toAbsolutePath
      context.become(
        withCallbackRegistryMap(
          newCallbackRegistryMap(
            currentCallbackRegistryMap,
            event,
            _ withoutCallbacksFor(absolutePath, recursive)
          )
        )
      )
    }

    case _ => log.error("MonitorActor received an unexpected message :( !")
  }

  /**
   * Return a new CallbackRegistryMap for a given Event type
   * @param cbRegistryMap
   * @param eventType WatchEvent.Kind[Path] Java7 Event type
   * @param modify a function to update the CallbackRegistry
   * @return Map[WatchEvent.Kind[Path], CallbackRegistry]
   */
  private[this] def newCallbackRegistryMap( cbRegistryMap: CallbackRegistryMap,
                                            eventType: WatchEvent.Kind[Path],
                                            modify: CallbackRegistry => CallbackRegistry): CallbackRegistryMap = {
    if (!cbRegistryMap.isDefinedAt(eventType))
      cbRegistryMap
    else {
      cbRegistryMap.updated(eventType, modify(cbRegistryMap(eventType)))
    }

  }

  /**
   * Retrieves the callbacks registered for a path for an Event type
   *
   * @param eventType WatchEvent.Kind[Path] Java7 Event type
   * @param path Path (Java type) to be registered
   * @return Option[Callbacks] for the path at the event type (Option[List[Path => Unit]])
   */
  def callbacksFor(cbRegistryMap: CallbackRegistryMap,
                   eventType: WatchEvent.Kind[Path],
                   path: Path): Option[Callbacks] = {
    cbRegistryMap.get(eventType) flatMap { _ callbacksFor(path) }
  }

  /**
   * Adds a path to be monitored by the WatchServiceTask. If specified, all
   * subdirectories will be recursively added to the WatchServiceTask.
   *
   * @param eventType WatchEvent.Kind[Path] Java7 Event type
   * @param recursive Boolean watch subdirectories of the given path
   * @param path Path (Java type) to be registered
   */
  private[this] def addPathToWatchServiceTask(eventType: WatchEvent.Kind[Path], modifier: Option[Modifier], path: Path, recursive: Boolean = false) {
    log.debug(s"Adding $path to WatchServiceTask")
    watchServiceTask.watch(path, eventType, modifier)
    if (recursive) forEachDir(path) { subDir =>
      log.debug(s"Adding $subDir to WatchServiceTask")
      watchServiceTask.watch(subDir, eventType, modifier)
    }
  }

  /**
   * Finds the callbacks for a given EventType and path and sends them all to
   * the CallbackActor pool to get processed
   *
   * @param event WatchEvent.Kind[Path] Java7 Event type
   * @param path Path (Java type) to be registered
   * @return Unit
   */
  def processCallbacksFor(
                           cbRegistryMap: CallbackRegistryMap,
                           event: WatchEvent.Kind[Path],
                           path: Path): Unit = {

    def processCallbacks(lookupPath: Path): Unit = {
      for {
        callbacks <- callbacksFor(cbRegistryMap, event, lookupPath)
        callback  <- callbacks
      } {
        log.debug(s"Sending callback for path: $path")
        callbackActors ! PerformCallback(path, callback)
      }
    }

    processCallbacks(path)
    // If event is ENTRY_DELETE or the path is a file, check for callbacks that
    // need to be fired for the directory the file is in
    if (event == ENTRY_DELETE || path.toFile.isFile) processCallbacks(path.getParent)
  }
}
