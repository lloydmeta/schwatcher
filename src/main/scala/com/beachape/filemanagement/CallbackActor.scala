package com.beachape.filemanagement

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.slf4j.Logging
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
  def apply() = Props(new CallbackActor)
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
class CallbackActor extends Actor with Logging {

  def receive = {
    case message: PerformCallback => performCallback(message)
    case _ => logger.error("Unknown message received")
  }

  /**
   * Apply's the callback inside a PeformCallback method inside
   * a PerformCallback message, passing in the message's path
   * @param message PerformCallback message containing Path and Callback
   */
  def performCallback(message: PerformCallback) {
    message.callback(message.path)
  }
}
