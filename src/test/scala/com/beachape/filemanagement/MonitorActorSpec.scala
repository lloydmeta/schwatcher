package com.beachape.filemanagement

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.beachape.filemanagement.Messages._
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds._
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.beachape.filemanagement.Messages.RegisterCallback
import scala.Some

class MonitorActorSpec extends TestKit(ActorSystem("testSystem"))
  with FunSpec
  with ShouldMatchers
  with BeforeAndAfter
  with ImplicitSender {

  val tempFile = java.io.File.createTempFile("fakeFile", ".log")
  tempFile.deleteOnExit()
  val dummyFunction: Path => Unit = { (path: Path) =>  val bleh = "lala"}

  var monitorActorRef = TestActorRef(new MonitorActor)
  var monitorActor = monitorActorRef.underlyingActor

  var counter = 0

  after {
    // Create a new actor for each test
    monitorActorRef = TestActorRef(new MonitorActor)
    monitorActor = monitorActorRef.underlyingActor
    counter = 0
  }

  val tempDirPath = Files.createTempDirectory("root")
  val tempDirLevel1Path = Files.createTempDirectory(tempDirPath, "level1")
  val tempDirLevel2Path = Files.createTempDirectory(tempDirLevel1Path, "level2")
  val tempFileInTempDir = Files.createTempFile(tempDirPath, "hello", ".there")

  tempDirPath.toFile.deleteOnExit()
  tempDirLevel1Path.toFile.deleteOnExit()
  tempDirLevel2Path.toFile.deleteOnExit()
  tempFileInTempDir.toFile.deleteOnExit()

  describe("construction via Props factory") {

    it("should throw an error when concurrency parameter is set to less than 1") {
      val thrown = intercept[IllegalArgumentException] {
        TestActorRef(MonitorActor(0))
      }
      thrown.getMessage should be("requirement failed: Callback concurrency requested is 0 but it should at least be 1")
    }

  }

  describe("methods testing") {

    describe("#addPathCallback") {

      it("should return the path used for registration") {
        monitorActor.addPathCallback(ENTRY_DELETE, tempFile.toPath, dummyFunction) should be(tempFile.toPath)
      }

      it("should allow the callback to be retrieved via callbacksForPath") {
        monitorActor.addPathCallback(ENTRY_CREATE, tempFile.toPath, dummyFunction)
        monitorActor.callbacksForPath(ENTRY_CREATE, tempFile.toPath).map(callbacks =>
          callbacks should contain (dummyFunction))
      }

    }

    describe("#recursivelyAddPathCallback") {

      it("should return the path used for registration") {
        monitorActor.recursivelyAddPathCallback(ENTRY_CREATE, tempDirPath, dummyFunction) should be(tempDirPath)
      }

      it("should add callbacks for all folders that exist under the path given") {
        monitorActor.recursivelyAddPathCallback(ENTRY_CREATE, tempDirPath, dummyFunction)
        monitorActor.callbacksForPath(ENTRY_CREATE, tempDirLevel1Path).map(callbacks =>
          callbacks should contain (dummyFunction))
        monitorActor.callbacksForPath(ENTRY_CREATE, tempDirLevel2Path).map(callbacks =>
          callbacks should contain (dummyFunction))
      }

      it("should add callbacks for a file path") {
        monitorActor.recursivelyAddPathCallback(ENTRY_CREATE, tempFileInTempDir, dummyFunction)
        monitorActor.callbacksForPath(ENTRY_CREATE, tempFileInTempDir).map(callbacks =>
          callbacks should contain (dummyFunction))
      }

      it("should not add callbacks recursively if given a file path") {
        monitorActor.recursivelyAddPathCallback(ENTRY_CREATE, tempFileInTempDir, dummyFunction)
        monitorActor.callbacksForPath(ENTRY_CREATE, tempDirLevel1Path).map(callbacks =>
          callbacks should not contain (dummyFunction))
        monitorActor.callbacksForPath(ENTRY_CREATE, tempDirLevel2Path).map(callbacks =>
          callbacks should not contain (dummyFunction))
      }

    }

    describe("#removeCallbacksForPath") {

      monitorActor.addPathCallback(ENTRY_DELETE, tempFile.toPath, dummyFunction) should be(tempFile.toPath)

      it("should return the path used") {
        monitorActor.removeCallbacksForPath(ENTRY_DELETE, tempFile.toPath) should be(tempFile.toPath)
      }

      it("should cause the callback retrieved for the path via callbacksForPath to be empty") {
        monitorActor.removeCallbacksForPath(ENTRY_DELETE, tempFile.toPath) should be(tempFile.toPath)
        monitorActor.callbacksForPath(ENTRY_DELETE, tempFile.toPath).isEmpty should be(true)
      }

    }

    describe("#recursivelyRemoveCallbacksForPath") {

      monitorActor.recursivelyAddPathCallback(ENTRY_CREATE, tempDirPath, dummyFunction)

      it("should return the path used for un-registration") {
        monitorActor.recursivelyRemoveCallbacksForPath(ENTRY_CREATE, tempDirPath) should be(tempDirPath)
      }

      it("should remove callbacks for all folders that exist under the path given") {
        monitorActor.recursivelyRemoveCallbacksForPath(ENTRY_CREATE, tempDirPath) should be(tempDirPath)
        monitorActor.callbacksForPath(ENTRY_CREATE, tempDirLevel1Path).isEmpty should be(true)
        monitorActor.callbacksForPath(ENTRY_CREATE, tempDirLevel2Path).isEmpty should be(true)
      }

      it("should remove callbacks for a file path") {
        monitorActor.recursivelyAddPathCallback(ENTRY_CREATE, tempFileInTempDir, dummyFunction)
        monitorActor.recursivelyRemoveCallbacksForPath(ENTRY_CREATE, tempFileInTempDir)
        monitorActor.callbacksForPath(ENTRY_CREATE, tempFileInTempDir).isEmpty should be(true)
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
        tempFile2.deleteOnExit()
        monitorActor.callbacksForPath(ENTRY_CREATE, tempFile2.toPath).isEmpty should be(true)
      }

    }

    describe("#processCallbacksForEventPath") {

      val addAndSend = { (number: Int, path: Path) =>
        counter += number
        testActor ! counter
      }

      it("should get the proper callback for a file path") {
        monitorActor.addPathCallback(ENTRY_CREATE, tempDirPath, addAndSend(6, _))
        monitorActor.addPathCallback(ENTRY_CREATE, tempDirLevel2Path, addAndSend(6, _))
        monitorActor.addPathCallback(ENTRY_CREATE, tempFileInTempDir, addAndSend(5, _))
        monitorActor.processCallbacksForEventPath(ENTRY_CREATE, tempFileInTempDir)()
        expectMsg(5)
      }

      it("should get the proper callback for a directory") {
        monitorActor.addPathCallback(ENTRY_MODIFY, tempDirPath, addAndSend(6, _))
        monitorActor.addPathCallback(ENTRY_MODIFY, tempDirLevel2Path, addAndSend(6, _))
        monitorActor.addPathCallback(ENTRY_MODIFY, tempFileInTempDir, addAndSend(5, _))
        monitorActor.processCallbacksForEventPath(ENTRY_MODIFY, tempDirPath)()
        expectMsg(6)
      }

      it("should properly send the causerPath to the callback function when provided in the second parameter list") {
        monitorActor.addPathCallback(ENTRY_DELETE, tempDirPath, (path => testActor ! path))
        monitorActor.addPathCallback(ENTRY_DELETE, tempDirLevel2Path, addAndSend(6, _))
        monitorActor.addPathCallback(ENTRY_DELETE, tempFileInTempDir, addAndSend(5, _))
        monitorActor.processCallbacksForEventPath(ENTRY_DELETE, tempDirPath)(tempFileInTempDir)
        expectMsg(tempFileInTempDir)
      }

    }

  }

  describe("messaging tests") {

    describe("RegisterCallback message type") {

      val callbackFunc = { (path: Path) => val receivedPath = path }

      it("should register a callback when given a file path") {
        val registerFileCallbackMessage = RegisterCallback(ENTRY_CREATE, false, tempFileInTempDir, callbackFunc)
        monitorActorRef ! registerFileCallbackMessage
        val Some(callbacks) = monitorActor.callbacksForPath(ENTRY_CREATE, tempFileInTempDir)
        callbacks.contains(callbackFunc) should be(true)
      }

      it("should register a callback when given a directory path") {
        val registerFileCallbackMessage = RegisterCallback(ENTRY_MODIFY, false, tempDirPath, callbackFunc)
        monitorActorRef ! registerFileCallbackMessage
        val Some(callbacks) = monitorActor.callbacksForPath(ENTRY_MODIFY, tempDirPath)
        callbacks.contains(callbackFunc) should be(true)
      }

      it("should register a callback recursively for a directory path") {
        val registerFileCallbackMessage = RegisterCallback(ENTRY_DELETE, true, tempDirPath, callbackFunc)
        monitorActorRef ! registerFileCallbackMessage
        val Some(callbacksForTempDirLevel1) = monitorActor.callbacksForPath(ENTRY_DELETE, tempDirLevel1Path)
        callbacksForTempDirLevel1.contains(callbackFunc) should be(true)
        val Some(callbacksForTempDirLevel2) = monitorActor.callbacksForPath(ENTRY_DELETE, tempDirLevel2Path)
        callbacksForTempDirLevel2.contains(callbackFunc) should be(true)
      }

      it("should not register a callback for a file inside a directory tree even when called recursively") {
        val registerFileCallbackMessage = RegisterCallback(ENTRY_CREATE, true, tempDirPath, callbackFunc)
        monitorActorRef ! registerFileCallbackMessage
        monitorActor.callbacksForPath(ENTRY_CREATE, tempFileInTempDir).isEmpty should be(true)
      }

    }

    describe("UnRegisterCallback message type") {

      val callbackFunc = { (path: Path) => val receivedPath = path }

      it("should un-register a callback when given a file path") {
        monitorActorRef ! RegisterCallback(ENTRY_CREATE, false, tempFileInTempDir, callbackFunc)
        monitorActorRef ! UnRegisterCallback(ENTRY_CREATE, false, tempFileInTempDir)
        monitorActor.callbacksForPath(ENTRY_CREATE, tempFileInTempDir).isEmpty should be(true)
      }

      it("should un-register a callback when given a directory path") {
        monitorActorRef ! RegisterCallback(ENTRY_DELETE, false, tempDirPath, callbackFunc)
        monitorActorRef ! UnRegisterCallback(ENTRY_DELETE, false, tempDirPath)
        monitorActor.callbacksForPath(ENTRY_DELETE, tempDirPath).isEmpty should be(true)
      }

      it("should un-register a callback recursively for a directory path") {
        monitorActorRef ! RegisterCallback(ENTRY_MODIFY, true, tempDirPath, callbackFunc)
        monitorActorRef ! UnRegisterCallback(ENTRY_MODIFY, true, tempDirPath)
        println(monitorActor.callbacksForPath(ENTRY_MODIFY, tempDirLevel1Path))
        monitorActor.callbacksForPath(ENTRY_MODIFY, tempDirLevel1Path).isEmpty should be(true)
        monitorActor.callbacksForPath(ENTRY_MODIFY, tempDirLevel2Path).isEmpty should be(true)
      }

      it("should not un-register a callback for a file inside a directory tree even when called recursively") {
        monitorActorRef ! RegisterCallback(ENTRY_CREATE, true, tempDirPath, callbackFunc)
        monitorActorRef ! RegisterCallback(ENTRY_CREATE, true, tempFileInTempDir, callbackFunc)
        monitorActorRef ! UnRegisterCallback(ENTRY_CREATE, true, tempDirPath)
        monitorActor.callbacksForPath(ENTRY_CREATE, tempFileInTempDir).isEmpty should be(false)
      }

    }

    describe("EventAtPath message type") {

      sealed case class TestResponse(message: String)
      val callbackFunc = { (path: Path) => testActor ! TestResponse(s"path is $path") }

      it("should cause callback to be fired for a registered file path") {
        val registerFileCallbackMessage = RegisterCallback(ENTRY_CREATE, false, tempFileInTempDir, callbackFunc)
        monitorActorRef ! registerFileCallbackMessage
        monitorActorRef ! EventAtPath(ENTRY_CREATE, tempFileInTempDir)
        expectMsg(TestResponse(s"path is $tempFileInTempDir"))
      }

      it("should cause callback to be fired for a registered directory path") {
        val registerFileCallbackMessage = RegisterCallback(ENTRY_MODIFY, false, tempDirPath, callbackFunc)
        monitorActorRef ! registerFileCallbackMessage
        monitorActorRef ! EventAtPath(ENTRY_MODIFY, tempFileInTempDir)
        expectMsg(TestResponse(s"path is $tempFileInTempDir"))
      }

      it("should case callbacks to to fired for a registered directory path AND the file path itself") {
        val registerFileCallbackMessageDirectory = RegisterCallback(ENTRY_DELETE, false, tempDirPath,
          path => testActor ! TestResponse(s"directory callback path is $path"))
        val registerFileCallbackMessageFile = RegisterCallback(ENTRY_DELETE, false, tempFileInTempDir,
          path => testActor ! TestResponse(s"file callback path is $path"))
        monitorActorRef ! registerFileCallbackMessageDirectory
        monitorActorRef ! registerFileCallbackMessageFile
        monitorActorRef ! EventAtPath(ENTRY_DELETE, tempFileInTempDir)
        expectMsgAllOf(
          TestResponse(s"directory callback path is $tempFileInTempDir"),
          TestResponse(s"file callback path is $tempFileInTempDir"))
      }

    }

  }

}
