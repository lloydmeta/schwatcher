package com.beachape.filemanagement

import akka.actor.Actor
import akka.agent.Agent
import akka.actor.Props
import scala.concurrent.duration._
import akka.util.Timeout
import java.nio.file._
import java.nio.file.StandardWatchEventKinds._
import com.beachape.filemanagement.RegistryTypes._
import com.typesafe.scalalogging.slf4j.Logging
import scala.reflect.io.File

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
  def apply(concurrency: Int) = Props(new MonitorActor(concurrency))
}

/**
 * Actor for registering feedbacks and delegating callback execution
 *
 * Should be instantiated with Props provided via companion object factory
 * method
 */
class MonitorActor(concerrency: Int = 5) extends Actor with Logging with RecursiveFileActions {

  implicit val timeout = Timeout(10 seconds)
  implicit val system = context.system

  private val eventTypeCallbackRegistryMap = Map(
    ENTRY_CREATE -> Agent(CallbackRegistry(ENTRY_CREATE)),
    ENTRY_MODIFY -> Agent(CallbackRegistry(ENTRY_MODIFY)),
    ENTRY_DELETE -> Agent(CallbackRegistry(ENTRY_DELETE))
  )

  def receive = {
    case _ => throw new IllegalArgumentException
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
    if (File(path.toString).exists)
      eventTypeCallbackRegistryMap.get(eventType).map(_ send (_ withPathCallback(path, callback)))
    else
      logger.info(s"Path '$path' does not exist, ignoring.")
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
    addPathCallback(eventType, path, callback)
    recursiveActionForPath(path) { (containedDirPath, attributes) =>
      addPathCallback(eventType, containedDirPath, callback)
    }
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
    eventTypeCallbackRegistryMap.get(eventType).map(_ send (_ withoutCallbacksForPath (path)))
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
    removeCallbacksForPath(eventType, path)
    recursiveActionForPath(path) { (containedDirPath, attributes) =>
        removeCallbacksForPath(eventType, containedDirPath)
    }
    path
  }

  /**
   * Retrieves the callbacks registered for a path for an Event type
   *
   * @param eventType WatchEvent.Kind[Path] Java7 Event type
   * @param path Path (Java type) to be registered
   * @return Option[Callbacks] for the path at the event type (Option[List[Path => Unit]])
   */
  def callbacksForPath(eventType: WatchEvent.Kind[Path], path: Path): Option[Callbacks] = {
    eventTypeCallbackRegistryMap.
      get(eventType).
      flatMap(registryAgent => registryAgent.await.callbacksForPath(path))
  }

}
