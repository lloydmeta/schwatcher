package com.beachape.filemanagement

import akka.actor.ActorRef
import collection.JavaConversions._
import com.typesafe.scalalogging.slf4j.Logging
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{WatchKey, WatchEvent, Path, FileSystems}


/**
 * Companion object for factory method
 */
object WatchServiceTask {
  def apply(notifyActor: ActorRef) = new WatchServiceTask(notifyActor)
}

/**
 * WatchService class that implements a Run method for passing into
 * a thread
 *
 * Largely inspired by http://www.javacodegeeks.com/2013/04/watchservice-combined-with-akka-actors.html
 * Takes care of registering paths to be monitored as well as
 * logic that takes care of properly shutting down and monitoring
 * the watcher thread
 */
class WatchServiceTask(notifyActor: ActorRef) extends Runnable with Logging {

  private val watchService = FileSystems.getDefault.newWatchService()

  /**
   * Not meant to be called directly
   *
   * As an object that extends the Runnable interface, this method
   * is required so that it can be passed into a Thread object and be run
   * when #start is called on the thread object
   */
  def run() {
    try {
      logger.debug("Waiting for file system events...")
      while (!Thread.currentThread().isInterrupted) {
        val key = watchService.take()
        key.pollEvents() foreach { event =>
          val relativePath = event.context().asInstanceOf[Path]
          val path = key.watchable().asInstanceOf[Path].resolve(relativePath)
          event.kind match {
            // Don't really have a choice here because of type erasure.
            case kind: WatchEvent.Kind[_] if List(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY).contains(kind) =>
              notifyActor ! EventAtPath(kind, path)
            case x => logger.warn(s"Unknown event $x")
          }
        }
        key.reset()
      }
    } catch {
      case e: InterruptedException =>
        logger.info("Interrupting, bye!")
    } finally {
      watchService.close()
    }
  }

  /**
   * Adds the path to the WatchService of this WatchServiceTask
   *
   * This allows the thread running the run() method to do take()
   * on the watchService to get a key for polling for events on
   * registered paths
   *
   * @param path Path (Java7) path
   * @param eventType WatchEvent.Kind[Path], one of ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE
   * @return WatchKey a Java7 WatchService WatchKey
   */
  def watch(path: Path, eventType: WatchEvent.Kind[Path]): WatchKey =
    path.register(watchService, eventType)

}
