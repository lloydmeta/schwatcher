package com.beachape.filemanagement

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import com.beachape.filemanagement.Messages.PerformCallback
import com.beachape.filemanagement.RegistryTypes._
import java.nio.file.Paths
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import scala.concurrent.duration._

class CallbackActorSpec extends TestKit(ActorSystem("testSystem"))
  with FunSpec
  with ShouldMatchers
  with BeforeAndAfter
  with ImplicitSender {

  val callbackActor = system.actorOf(CallbackActor())
  val tmpDirPath = Paths get System.getProperty("java.io.tmpdir")

  var counter = 0

  before {
    counter = 0
  }

  describe("#receive") {

    val dummyFunction: Callback = { path => counter += 1}
    val dummySendFunction: Callback = { path => testActor ! "Up yours!" }

    it("should take a PerformCallback object and perform the callback") {
      callbackActor ! PerformCallback(tmpDirPath, dummyFunction)
      within(1 second) { counter should be(1) }
    }

    it("should work with callbacks that send messages") {
      callbackActor ! PerformCallback(tmpDirPath, dummySendFunction)
      expectMsg("Up yours!")
    }

  }
}
