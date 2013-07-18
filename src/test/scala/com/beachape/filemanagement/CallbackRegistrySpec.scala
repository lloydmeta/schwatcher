package com.beachape.filemanagement

import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{Path, Paths, Files}
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{PrivateMethodTester, BeforeAndAfter, FunSpec}


class CallbackRegistrySpec extends FunSpec
  with PrivateMethodTester
  with ShouldMatchers
  with BeforeAndAfter {

  val dummyFunction: Path => Unit = { (path: Path) =>  val bleh = "lala"}

  describe("companion factory object") {

    it("should create an instance of CallbackRegistry") {
      CallbackRegistry(ENTRY_CREATE).isInstanceOf[CallbackRegistry] should be(true)
    }

    it("should create a CallbackRegistry with the same event type passed to the factory") {
      CallbackRegistry(ENTRY_CREATE).eventType should be(ENTRY_CREATE)
      CallbackRegistry(ENTRY_DELETE).eventType should be(ENTRY_DELETE)
      CallbackRegistry(ENTRY_MODIFY).eventType should be(ENTRY_MODIFY)
    }
  }

  describe("#withPathCallback") {

    val registry = CallbackRegistry(ENTRY_CREATE)
    val tmpDirPath = Paths get System.getProperty("java.io.tmpdir")
    val callback = { (path: Path) => println(path.toString) }

    it("should create a new CallbackRegistry") {
      registry.withPathCallback(tmpDirPath, {
        path =>
      }).isInstanceOf[CallbackRegistry] should be(true)
    }

    it("should create a new CallbackRegistry that has the proper callback registered for the given path") {
      val newRegistry = registry.withPathCallback(tmpDirPath, callback)
      newRegistry.callbacksForPath(tmpDirPath).map(callbackList => callbackList.contains(callback) should be(true))
    }

    it("should not affect the old CallbackRegistry") {
      registry.withPathCallback(tmpDirPath, callback)
      registry.callbacksForPath(tmpDirPath).map(callbackList => callbackList.contains(callback) should be(false))
    }

    it("should be chainable and allow different callbacks to be registered for the same path ") {
      val callback1 = { (path: Path) =>
          val test = 1 + 1
      }
      val callback2 = { (path: Path) =>
          val test = 1 + 2
      }
      val callback3 = { (path: Path) =>
          val test = 1 + 3
      }
      val newRegistry = registry.withPathCallback(tmpDirPath, callback1).
        withPathCallback(tmpDirPath, callback2).
        withPathCallback(tmpDirPath, callback3)
      newRegistry.callbacksForPath(tmpDirPath).map(callbacks => callbacks.length should be(3))
    }

  }

  describe("#withoutCallbacksForPath") {

    val tmpDirPath = Paths get System.getProperty("java.io.tmpdir")
    val newTmpDir = Files.createTempDirectory(tmpDirPath, "test")
    newTmpDir.toFile.deleteOnExit()

    val callback = {
      (path: Path) => println(path.toString)
    }
    val registry = CallbackRegistry(ENTRY_CREATE).withPathCallback(tmpDirPath, callback)

    it("should return a CallbackRegistry that does not callbacks for the path passed in") {
      val newRegistry = registry.withoutCallbacksForPath(tmpDirPath)
      newRegistry.callbacksForPath(tmpDirPath).isEmpty should be (true)
    }

    it("should return a CallbackRegistry without raising an exception even if the path was never registered in the first place") {
      val bareRegistry = CallbackRegistry(ENTRY_CREATE).withPathCallback(newTmpDir, callback)
      bareRegistry.withoutCallbacksForPath(tmpDirPath).callbacksForPath(tmpDirPath).isEmpty should be(true)
      bareRegistry.withoutCallbacksForPath(tmpDirPath).callbacksForPath(newTmpDir).isEmpty should be(false)
    }
  }

  describe("recursive methods") {

    val tempDirPath = Files.createTempDirectory("root")
    val tempDirLevel1Path = Files.createTempDirectory(tempDirPath, "level1")
    val tempDirLevel2Path = Files.createTempDirectory(tempDirLevel1Path, "level2")
    val tempFileInTempDir = Files.createTempFile(tempDirPath, "hello", ".there")
    tempDirPath.toFile.deleteOnExit()
    tempDirLevel1Path.toFile.deleteOnExit()
    tempDirLevel2Path.toFile.deleteOnExit()
    tempFileInTempDir.toFile.deleteOnExit()

    describe("#withPathCallbackRecursive") {

      val registryWithRecursive = CallbackRegistry(ENTRY_CREATE).withPathCallbackRecursive(tempDirPath, dummyFunction)

      it("should add callbacks for all folders that exist under the path given") {
        registryWithRecursive.callbacksForPath(tempDirLevel1Path).map(callbacks =>
          callbacks should contain (dummyFunction))
        registryWithRecursive.callbacksForPath(tempDirLevel2Path).map(callbacks =>
          callbacks should contain (dummyFunction))
      }

      it("should add callbacks for a file path") {
        val registry = CallbackRegistry(ENTRY_CREATE).withPathCallbackRecursive(tempFileInTempDir, dummyFunction)
        registry.callbacksForPath(tempFileInTempDir).map(callbacks =>
          callbacks should contain (dummyFunction))
      }

      it("should not add callbacks recursively if given a file path") {
        val registry = CallbackRegistry(ENTRY_CREATE).withPathCallbackRecursive(tempFileInTempDir, dummyFunction)
        registry.callbacksForPath(tempFileInTempDir).map(callbacks =>
          callbacks should contain (dummyFunction))
        registry.callbacksForPath(tempDirLevel1Path).map(callbacks =>
          callbacks should not contain (dummyFunction))
        registry.callbacksForPath(tempDirLevel2Path).map(callbacks =>
          callbacks should not contain (dummyFunction))
      }

    }

    describe("#withoutCallbacksForPathRecursive") {

      it("should cause the callback retrieved for the path via callbacksForPath to be empty") {
        val registry = CallbackRegistry(ENTRY_CREATE).withPathCallbackRecursive(tempDirPath, dummyFunction)
        val registryWithoutCallbacks = registry.withoutCallbacksForPathRecursive(tempDirPath)
        registryWithoutCallbacks.callbacksForPath(tempDirLevel2Path).isEmpty should be(true)
      }

      it("should remove callbacks when given a path to a file") {
        val registry = CallbackRegistry(ENTRY_CREATE).withPathCallbackRecursive(tempFileInTempDir, dummyFunction)
        val registryWithoutCallbacks = registry.withoutCallbacksForPathRecursive(tempFileInTempDir)
        registryWithoutCallbacks.callbacksForPath(tempFileInTempDir).isEmpty should be(true)
      }

    }
  }

  describe("#callbacksForPath") {

    val registry = CallbackRegistry(ENTRY_CREATE)
    val tmpDirPath = Paths get System.getProperty("java.io.tmpdir")

    it("should None by default") {
      registry.callbacksForPath(tmpDirPath).isEmpty should be(true)
    }

    it("should not be empty for a path once that path has been registered with a callback") {
      val callback = {
        (path: Path) => println(path.toString)
      }
      val newRegistry = registry.withPathCallback(tmpDirPath, callback)
      newRegistry.callbacksForPath(tmpDirPath).isEmpty should be(false)
    }

    it("should return Some[List[Callback]] that contains the callback that was registered with a path ") {
      val callback = {
        (path: Path) => println(path.toString)
      }
      val newRegistry = registry.withPathCallback(tmpDirPath, callback)
      newRegistry.callbacksForPath(tmpDirPath).map(callbackList => callbackList.contains(callback) should be(true))
    }

    it("should return Some[List[Callback]] that are individually #apply able") {
      var sum = 0
      val callback1 = { (path: Path) =>
        sum += 1
      }
      val callback2 = { (path: Path) =>
        sum += 2
      }
      val callback3 = { (path: Path) =>
        sum += 3
      }
      val newRegistry = registry.withPathCallback(tmpDirPath, callback1).
        withPathCallback(tmpDirPath, callback2).
        withPathCallback(tmpDirPath, callback3)
      for {
        callbacks <- newRegistry.callbacksForPath(tmpDirPath)
        callback <- callbacks
      } {
        callback(tmpDirPath)
      }
      sum should be (6)
    }
  }
}
