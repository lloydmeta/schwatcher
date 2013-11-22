package com.beachape.filemanagement

import akka.actor.{Actor, Props}
import com.beachape.filemanagement.Messages.PerformCallback

/**
 * Companion object for CallbackActor to allow easy creation of
 * props via apply method
 */
object CallbackActor {
  /**
   * Factory method for props required to spawn a MonitorActor
   * @return Props for spawning an actor
   */
  def apply() = Props(classOf[CallbackActor])
}

/**
 * Actor that performs Callbacks
 *
 * This allows us to rather easily control concurrency in the
 * parent MonitorActor by simply spawning a certain number of
 * actors in the routing pool.
 *
 * Should be created via companion method's props
 */
class CallbackActor extends Actor {
  def receive = {
    case PerformCallback(path, callback) => callback(path)
  }
}
