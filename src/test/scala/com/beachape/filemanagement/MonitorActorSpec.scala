package com.beachape.filemanagement

import akka.actor.ActorSystem
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import akka.testkit.{TestActorRef, TestKit}
import java.nio.file.{WatchEvent, Path}
import java.nio.file.StandardWatchEventKinds._

class MonitorActorSpec extends TestKit(ActorSystem("testSystem"))
  with FunSpec
  with ShouldMatchers
  with BeforeAndAfter {

  val tempFile = java.io.File.createTempFile("fakeFile", ".log")
  val monitorActorRef = TestActorRef(new MonitorActor)
  val monitorActor = monitorActorRef.underlyingActor
  val dummyFunction: Path => Unit = { (path: Path) =>  val bleh = "lala"}

  describe("methods testing") {

    describe("#addPathCallback") {

      it("should return the path used for registration") {
        monitorActor.addPathCallback(ENTRY_CREATE, tempFile.toPath, dummyFunction) should be(tempFile.toPath)
      }

      it("should allow the callback to be retrieved via callbacksForPath") {
        monitorActor.addPathCallback(ENTRY_CREATE, tempFile.toPath, dummyFunction)
        monitorActor.callbacksForPath(ENTRY_CREATE, tempFile.toPath).map(callbacks =>
          callbacks should contain (dummyFunction))
      }

    }

    describe("#callbacksForPath") {

      monitorActor.addPathCallback(ENTRY_CREATE, tempFile.toPath, dummyFunction)

      it("should return Some[Callbacks] that contains prior registered callbacks for a path") {
        monitorActor.callbacksForPath(ENTRY_CREATE, tempFile.toPath).map(callbacks =>
          callbacks should contain (dummyFunction))
      }

      it("should return Some[Callbacks] that does not contain callbacks for paths never registered") {
        val tempFile2 = java.io.File.createTempFile("fakeFile2", ".log")
        monitorActor.callbacksForPath(ENTRY_CREATE, tempFile2.toPath).isEmpty should be(true)
      }

    }

  }

}
