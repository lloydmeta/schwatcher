package com.beachape.filemanagement

import akka.actor.ActorSystem

/**
 * Just a singleton object holding a reference to an ActorSystem
 */
object MainActorSystem {
  implicit val system = ActorSystem("actorSystem")
}