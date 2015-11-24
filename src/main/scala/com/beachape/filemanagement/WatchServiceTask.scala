package com.beachape.filemanagement

import akka.actor.ActorRef
import scala.concurrent.duration._
import collection.JavaConverters._
import com.beachape.filemanagement.Messages.EventAtPath
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{ WatchKey, WatchEvent, Path, FileSystems }
import java.nio.file.WatchEvent.{ Kind, Modifier }

/**
 * Companion object for factory method
 */
object WatchServiceTask {

  val SupportedEvents: Set[Kind[_]] = Set(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)

  /**
   * Creates a WatchServiceTask tied to an actor
   */
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
class WatchServiceTask(notifyActor: ActorRef) extends Runnable {

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
      while (!Thread.currentThread().isInterrupted) {
        val key = watchService.take()
        key.pollEvents().asScala foreach { event =>
          event.kind match {
            // Don't really have a choice here because of type erasure.
            case kind: WatchEvent.Kind[_] if WatchServiceTask.SupportedEvents.contains(kind) => {
              /*
                * In the case of ENTRY_CREATE, ENTRY_DELETE, and ENTRY_MODIFY events the context is a Path.
                * Since we have filtered for this in the guard for this pattern, we can do a cast to Path.
               */
              val relativePath = event.context().asInstanceOf[Path]
              val path = contextAbsolutePath(key, relativePath)
              notifyActor ! EventAtPath(kind, path)
            }
            case _ => // do nothing
          }
        }
        key.reset()
      }
    } catch {
      case e: InterruptedException =>
    } finally {
      stopService()
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
   * @param modifier  Option[Modifier], the modifiers, if any, that modify how the object is registered
   * @param eventTypes one or more WatchEvent.Kind[_], each being one of ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE
   * @return Option[WatchKey] a Java7 WatchService WatchKey
   */
  def watch(path: Path, modifier: Option[Modifier], eventTypes: WatchEvent.Kind[_]*): Option[WatchKey] = {
    val fileAtPath = path.toFile
    if (fileAtPath.isDirectory) {
      modifier match {
        case Some(mod) => Some(path.register(watchService, eventTypes.distinct.toArray, mod))
        case None => Some(path.register(watchService, eventTypes.distinct: _*))
      }
    } else if (fileAtPath.isFile) {
      modifier match {
        case Some(mod) => Some(path.getParent.register(watchService, eventTypes.distinct.toArray, mod))
        case None => Some(path.getParent.register(watchService, eventTypes.distinct: _*))
      }
    } else {
      None
    }
  }

  /**
   * Returns the absolute path for a given event.context().asInstancOf[Path]
   * path taken from a WatchService event
   *
   * This is actually taken from http://www.javacodegeeks.com/2013/04/watchservice-combined-with-akka-actors.html
   * The way it works is a context is taken from the events originating
   * from a WatchKey, which itself is registered to an actual watchable
   * object. In this case, the watchable object is a Path. By using
   * the watchable object as a path and calling resolve, passing in the relative
   * path obtained from the context, we obtain the "full" relative-to-watchable path.
   * Afterwards, toAbsolutePath is called to get the real absolute path.
   *
   * @param key WatchKey
   * @param contextPath Path relative to the key's watchable
   * @return Path
   */
  def contextAbsolutePath(key: WatchKey, contextPath: Path): Path = {
    key.watchable().asInstanceOf[Path].resolve(contextPath).toAbsolutePath
  }

  /**
   * Stops the WatchService current
   */
  def stopService() {
    watchService.close()
  }
}
