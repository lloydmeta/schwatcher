package com.beachape.filemanagement

import akka.actor.ActorSystem
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import akka.testkit.{TestActorRef, TestKit}
import java.nio.file.Files
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.Path

class MonitorActorSpec extends TestKit(ActorSystem("testSystem"))
  with FunSpec
  with ShouldMatchers
  with BeforeAndAfter {

  val tempFile = java.io.File.createTempFile("fakeFile", ".log")
  val dummyFunction: Path => Unit = { (path: Path) =>  val bleh = "lala"}

  var monitorActorRef = TestActorRef(new MonitorActor)
  var monitorActor = monitorActorRef.underlyingActor

  after {
    // Create a new actor for each test
    monitorActorRef = TestActorRef(new MonitorActor)
    monitorActor = monitorActorRef.underlyingActor
  }

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

    describe("#recursivelyAddPathCallback") {

      val tempDirPath = Files.createTempDirectory("root")
      val tempDirLevel1Path = Files.createTempDirectory(tempDirPath, "level1")
      val tempDirLevel2Path = Files.createTempDirectory(tempDirLevel1Path, "level2")
      val tempFileInTempDir = Files.createTempFile(tempDirPath, "hello", ".there")

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

      monitorActor.addPathCallback(ENTRY_CREATE, tempFile.toPath, dummyFunction) should be(tempFile.toPath)

      it("should return the path used") {
        monitorActor.removeCallbacksForPath(ENTRY_CREATE, tempFile.toPath) should be(tempFile.toPath)
      }

      it("should cause the callback retrieved for the path via callbacksForPath to be empty") {
        monitorActor.removeCallbacksForPath(ENTRY_CREATE, tempFile.toPath) should be(tempFile.toPath)
        monitorActor.callbacksForPath(ENTRY_CREATE, tempFile.toPath).isEmpty should be(true)
      }

    }

    describe("#recursivelyRemoveCallbacksForPath") {

      val tempDirPath = Files.createTempDirectory("root")
      val tempDirLevel1Path = Files.createTempDirectory(tempDirPath, "level1")
      val tempDirLevel2Path = Files.createTempDirectory(tempDirLevel1Path, "level2")
      val tempFileInTempDir = Files.createTempFile(tempDirPath, "hello", ".there")

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
        monitorActor.callbacksForPath(ENTRY_CREATE, tempFile2.toPath).isEmpty should be(true)
      }

    }

  }

}
