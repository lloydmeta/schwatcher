package com.beachape.filemanagement

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.beachape.filemanagement.Messages._
import com.beachape.filemanagement.RegistryTypes.Callback
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{Files, Path, WatchEvent}
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSpec
import org.scalatest.PrivateMethodTester
import org.scalatest.matchers.ShouldMatchers
import scala.concurrent.duration._

class MonitorActorSpec extends TestKit(ActorSystem("testSystem"))
  with FunSpec
  with ShouldMatchers
  with BeforeAndAfter
  with ImplicitSender
  with PrivateMethodTester {

  val tempFile = java.io.File.createTempFile("fakeFile", ".log")
  tempFile.deleteOnExit()
  val dummyFunction: Path => Unit = { (path: Path) =>  val bleh = "lala"}

  var monitorActorRef = TestActorRef(new MonitorActor)
  var monitorActor = monitorActorRef.underlyingActor
  var counter = 0

  val modifyCallbackRegistry = PrivateMethod[Unit]('modifyCallbackRegistry)

  // utility functions for testing the modifyCallbackRegistry private method
  def addCallbackFor(event: WatchEvent.Kind[Path], path: Path, callback: Callback, recursive: Boolean = true): Unit = {
    monitorActor invokePrivate modifyCallbackRegistry(event, { registry: CallbackRegistry =>
      registry.withCallbackFor(path, callback, recursive)
    })
    // monitorActorRef ! RegisterCallback(event, recursive = recursive, path, callback)
  }

  def removeCallbacksFor(event: WatchEvent.Kind[Path], path: Path, recursive: Boolean = true): Unit = {
    monitorActor invokePrivate modifyCallbackRegistry(event, { registry: CallbackRegistry =>
      registry.withoutCallbacksFor(path, recursive)
    })
  }

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

    describe("#modifyCallbackRegistry") {

      it("should allow callbacks to be added to the registry") {
        addCallbackFor(ENTRY_CREATE, tempFile.toPath, dummyFunction)
        monitorActor.callbacksFor(ENTRY_CREATE, tempFile.toPath).isEmpty should be(false)
        monitorActor.callbacksFor(ENTRY_CREATE, tempFile.toPath) foreach { callbacks =>
          callbacks should contain(dummyFunction)
        }
      }

      it("should allow callbacks to be removed from the registry") {
        addCallbackFor(ENTRY_CREATE, tempFile.toPath, dummyFunction)
        removeCallbacksFor(ENTRY_CREATE, tempFile.toPath)
        monitorActor.callbacksFor(ENTRY_CREATE, tempFile.toPath).isEmpty should be(true)
      }
    }

    describe("adding callbacks recursively") {

      it("should add callbacks for all folders that exist under the path given") {
        addCallbackFor(ENTRY_CREATE, tempDirPath, dummyFunction, true)
        monitorActor.callbacksFor(ENTRY_CREATE, tempDirLevel1Path).map(callbacks =>
          callbacks should contain (dummyFunction))
        monitorActor.callbacksFor(ENTRY_CREATE, tempDirLevel2Path).map(callbacks =>
          callbacks should contain (dummyFunction))
      }

      it("should add callbacks for a file path") {
        addCallbackFor(ENTRY_CREATE, tempFileInTempDir, dummyFunction, true)
        monitorActor.callbacksFor(ENTRY_CREATE, tempFileInTempDir).map(callbacks =>
          callbacks should contain (dummyFunction))
      }

      it("should not add callbacks recursively if given a file path") {
        addCallbackFor(ENTRY_CREATE, tempFileInTempDir, dummyFunction, true)
        monitorActor.callbacksFor(ENTRY_CREATE, tempDirLevel1Path).map(callbacks =>
          callbacks should not contain dummyFunction)
        monitorActor.callbacksFor(ENTRY_CREATE, tempDirLevel2Path).map(callbacks =>
          callbacks should not contain dummyFunction)
      }

    }

    describe("recursively removing callbacks") {

      addCallbackFor(ENTRY_CREATE, tempDirPath, dummyFunction, true)

      it("should remove callbacks for all folders that exist under the path given") {
        removeCallbacksFor(ENTRY_CREATE, tempDirPath, true)
        monitorActor.callbacksFor(ENTRY_CREATE, tempDirLevel1Path).isEmpty should be(true)
        monitorActor.callbacksFor(ENTRY_CREATE, tempDirLevel2Path).isEmpty should be(true)
      }

      it("should remove callbacks for a file path") {
        addCallbackFor(ENTRY_CREATE, tempFileInTempDir, dummyFunction, true)
        removeCallbacksFor(ENTRY_CREATE, tempFileInTempDir, true)
        monitorActor.callbacksFor(ENTRY_CREATE, tempFileInTempDir).isEmpty should be(true)
      }

    }

    describe("#callbacksFor") {

      addCallbackFor(ENTRY_CREATE, tempFile.toPath, dummyFunction)

      it("should return Some[Callbacks] that contains prior registered callbacks for a path") {
        monitorActor.callbacksFor(ENTRY_CREATE, tempFile.toPath) foreach { callbacks =>
          callbacks should contain (dummyFunction) }
      }

      it("should return Some[Callbacks] that does not contain callbacks for paths never registered") {
        val tempFile2 = java.io.File.createTempFile("fakeFile2", ".log")
        tempFile2.deleteOnExit()
        monitorActor.callbacksFor(ENTRY_CREATE, tempFile2.toPath).isEmpty should be(true)
      }

    }

    describe("messaging tests") {

      describe("RegisterCallback message type") {

        val callbackFunc = { (path: Path) => val receivedPath = path }

        it("should register a callback when given a file path") {
          val registerFileCallbackMessage = RegisterCallback(ENTRY_CREATE, recursive = false, tempFileInTempDir, callbackFunc)
          monitorActorRef ! registerFileCallbackMessage
          val Some(callbacks) = monitorActor.callbacksFor(ENTRY_CREATE, tempFileInTempDir)
          callbacks.contains(callbackFunc) should be(true)
        }

        it("should register a callback when given a directory path") {
          val registerFileCallbackMessage = RegisterCallback(ENTRY_MODIFY, recursive = false, tempDirPath, callbackFunc)
          monitorActorRef ! registerFileCallbackMessage
          val Some(callbacks) = monitorActor.callbacksFor(ENTRY_MODIFY, tempDirPath)
          callbacks.contains(callbackFunc) should be(true)
        }

        it("should register a callback recursively for a directory path") {
          val registerFileCallbackMessage = RegisterCallback(ENTRY_DELETE, recursive = true, tempDirPath, callbackFunc)
          monitorActorRef ! registerFileCallbackMessage
          val Some(callbacksForTempDirLevel1) = monitorActor.callbacksFor(ENTRY_DELETE, tempDirLevel1Path)
          callbacksForTempDirLevel1.contains(callbackFunc) should be(true)
          val Some(callbacksForTempDirLevel2) = monitorActor.callbacksFor(ENTRY_DELETE, tempDirLevel2Path)
          callbacksForTempDirLevel2.contains(callbackFunc) should be(true)
        }

        it("should not register a callback for a file inside a directory tree even when called recursively") {
          val registerFileCallbackMessage = RegisterCallback(ENTRY_CREATE, recursive = true, tempDirPath, callbackFunc)
          monitorActorRef ! registerFileCallbackMessage
          monitorActor.callbacksFor(ENTRY_CREATE, tempFileInTempDir).isEmpty should be(true)
        }

      }

      describe("UnRegisterCallback message type") {

        val callbackFunc = { (path: Path) => val receivedPath = path }

        it("should un-register a callback when given a file path") {
          monitorActorRef ! RegisterCallback(ENTRY_CREATE, recursive = false, tempFileInTempDir, callbackFunc)
          monitorActorRef ! UnRegisterCallback(ENTRY_CREATE, recursive = false, tempFileInTempDir)
          monitorActor.callbacksFor(ENTRY_CREATE, tempFileInTempDir).isEmpty should be(true)
        }

        it("should un-register a callback when given a directory path") {
          monitorActorRef ! RegisterCallback(ENTRY_DELETE, recursive = false, tempDirPath, callbackFunc)
          monitorActorRef ! UnRegisterCallback(ENTRY_DELETE, recursive = false, tempDirPath)
          monitorActor.callbacksFor(ENTRY_DELETE, tempDirPath).isEmpty should be(true)
        }

        it("should un-register a callback recursively for a directory path") {
          monitorActorRef ! RegisterCallback(ENTRY_MODIFY, recursive = true, tempDirPath, callbackFunc)
          monitorActorRef ! UnRegisterCallback(ENTRY_MODIFY, recursive = true, tempDirPath)
          println(monitorActor.callbacksFor(ENTRY_MODIFY, tempDirLevel1Path))
          monitorActor.callbacksFor(ENTRY_MODIFY, tempDirLevel1Path).isEmpty should be(true)
          monitorActor.callbacksFor(ENTRY_MODIFY, tempDirLevel2Path).isEmpty should be(true)
        }

        it("should not un-register a callback for a file inside a directory tree even when called recursively") {
          monitorActorRef ! RegisterCallback(ENTRY_CREATE, recursive = true, tempDirPath, callbackFunc)
          monitorActorRef ! RegisterCallback(ENTRY_CREATE, recursive = true, tempFileInTempDir, callbackFunc)
          monitorActorRef ! UnRegisterCallback(ENTRY_CREATE, recursive = true, tempDirPath)
          monitorActor.callbacksFor(ENTRY_CREATE, tempFileInTempDir).isEmpty should be(false)
        }

      }

      describe("EventAtPath message type") {

        sealed case class TestResponse(message: String)
        val callbackFunc = { (path: Path) => testActor ! TestResponse(s"path is $path") }

        it("should cause callback to be fired for a registered file path") {
          val registerFileCallbackMessage = RegisterCallback(ENTRY_CREATE, recursive = false, tempFileInTempDir, callbackFunc)
          monitorActorRef ! registerFileCallbackMessage
          monitorActorRef ! EventAtPath(ENTRY_CREATE, tempFileInTempDir)
          expectMsg(TestResponse(s"path is $tempFileInTempDir"))
        }

        it("should cause callback to be fired for a registered directory path") {
          val registerFileCallbackMessage = RegisterCallback(ENTRY_MODIFY, recursive = false, tempDirPath, callbackFunc)
          monitorActorRef ! registerFileCallbackMessage
          monitorActorRef ! EventAtPath(ENTRY_MODIFY, tempFileInTempDir)
          expectMsg(TestResponse(s"path is $tempFileInTempDir"))
        }

        it("should cause callbacks to to fired for a registered directory path AND the file path itself") {
          val registerFileCallbackMessageDirectory = RegisterCallback(ENTRY_DELETE, recursive = false, tempDirPath,
            path => testActor ! TestResponse(s"directory callback path is $path"))
          val registerFileCallbackMessageFile = RegisterCallback(ENTRY_DELETE, recursive = false, tempFileInTempDir,
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

    describe("#processCallbacksFor") {

      val callback = { path: Path => testActor ! path }

      it("should get the proper callback for a file path") {
        addCallbackFor(ENTRY_CREATE, tempDirPath, callback)
        addCallbackFor(ENTRY_CREATE, tempDirLevel2Path, callback)
        addCallbackFor(ENTRY_CREATE, tempFileInTempDir, callback)
        monitorActor.processCallbacksFor(ENTRY_CREATE, tempFileInTempDir)
        expectMsg(tempFileInTempDir)
      }

      it("should get the proper callback for a directory") {
        addCallbackFor(ENTRY_MODIFY, tempDirPath, callback)
        addCallbackFor(ENTRY_MODIFY, tempDirPath, callback)
        addCallbackFor(ENTRY_MODIFY, tempDirLevel2Path, callback)
        addCallbackFor(ENTRY_MODIFY, tempFileInTempDir, callback)
        monitorActor.processCallbacksFor(ENTRY_MODIFY, tempDirPath)
        expectMsg(tempDirPath)
      }

    }

  }

}
