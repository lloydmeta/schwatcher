package com.beachape.filemanagement

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.beachape.filemanagement.RegistryTypes._
import java.nio.file.Paths
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.beachape.filemanagement.Messages.PerformCallback

class CallbackActorSpec extends TestKit(ActorSystem("testSystem"))
  with FunSpec
  with ShouldMatchers
  with BeforeAndAfter
  with ImplicitSender {

  val callbackActorRef = TestActorRef(new CallbackActor)
  val callbackActor = callbackActorRef.underlyingActor
  val tmpDirPath = Paths get System.getProperty("java.io.tmpdir")

  var counter = 0

  before {
    counter = 0
  }

  describe("#performCallback") {

    val dummyFunction: Callback = { path => counter += 1}
    val dummySendFunction: Callback = { path => testActor ! "Up yours!" }

    it("should take a PerformCallback object and perform the callback") {
      callbackActor.performCallback(PerformCallback(tmpDirPath, dummyFunction))
      counter should be(1)
    }

    it("should work with callbacks that send messages") {
      callbackActor.performCallback(PerformCallback(tmpDirPath, dummySendFunction))
      expectMsg("Up yours!")
    }

  }
}
