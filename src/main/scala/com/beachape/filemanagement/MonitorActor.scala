package com.beachape.filemanagement

import akka.actor.Actor
import akka.agent.Agent
import akka.actor.Props
import scala.concurrent.duration._
import akka.util.Timeout
import java.nio.file.{WatchEvent, Path}
import java.nio.file.StandardWatchEventKinds._
import com.beachape.filemanagement.RegistryTypes._
import com.beachape.filemanagement.MainActorSystem._

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
class MonitorActor(concerrency: Int = 5) extends Actor {

  implicit val timeout = Timeout(10 seconds)

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
   * @param eventType WatchEvent.Kind[Path] Java7 Event type
   * @param path Path (Java type) to be registered
   * @param callback Callback function, Path => Init
   * @return Path used for registration
   */
  def addPathCallback(eventType: WatchEvent.Kind[Path], path: Path, callback: Callback): Path = {
    eventTypeCallbackRegistryMap.get(eventType).map(_ send (_ withPathCallback(path, callback)))
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
