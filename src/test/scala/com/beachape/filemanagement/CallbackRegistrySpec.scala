package com.beachape.filemanagement

import java.nio.file.{Path, Paths, Files}
import org.scalatest._

class CallbackRegistrySpec
    extends FunSpec
    with PrivateMethodTester
    with Matchers
    with BeforeAndAfter {

  val dummyFunction: Path => Unit = { (path: Path) =>
    val bleh = "lala"
  }

  describe("companion factory object") {

    it("should create an instance of CallbackRegistry") {
      CallbackRegistry().isInstanceOf[CallbackRegistry] should be(true)
    }
  }

  describe("#withCallbackFor") {

    val registry   = CallbackRegistry()
    val tmpDirPath = Paths get System.getProperty("java.io.tmpdir")
    val callback = { (path: Path) =>
      println(path.toString)
    }

    it("should create a new CallbackRegistry") {
      registry
        .withCallbackFor(tmpDirPath, { path =>
          })
        .isInstanceOf[CallbackRegistry] should be(true)
    }

    it(
      "should create a new CallbackRegistry that has the proper callback registered for the given path") {
      val newRegistry = registry.withCallbackFor(tmpDirPath, callback)
      newRegistry
        .callbacksFor(tmpDirPath)
        .map(callbackList => callbackList.contains(callback) should be(true))
    }

    it("should not affect the old CallbackRegistry") {
      registry.withCallbackFor(tmpDirPath, callback)
      registry
        .callbacksFor(tmpDirPath)
        .map(callbackList => callbackList.contains(callback) should be(false))
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
      val newRegistry = registry
        .withCallbackFor(tmpDirPath, callback1)
        .withCallbackFor(tmpDirPath, callback2)
        .withCallbackFor(tmpDirPath, callback3)
      newRegistry.callbacksFor(tmpDirPath).map(callbacks => callbacks.length should be(3))
    }

    it(
      "should obey the bossy argument and register only 1 callback for a path passed multiple times") {
      val pathsWithCallbacks = Stream.fill(100)((tmpDirPath, callback))
      val registryCompleted = pathsWithCallbacks.foldLeft(registry) { (acc, pC) =>
        val (path, callback) = pC
        acc.withCallbackFor(path, callback, bossy = true)
      }
      registryCompleted.callbacksFor(tmpDirPath).size should be(1)
    }

  }

  describe("#withoutCallbacksFor") {

    val tmpDirPath = Paths get System.getProperty("java.io.tmpdir")
    val newTmpDir  = Files.createTempDirectory(tmpDirPath, "test")
    newTmpDir.toFile.deleteOnExit()

    val callback = { (path: Path) =>
      println(path.toString)
    }
    val registry = CallbackRegistry().withCallbackFor(tmpDirPath, callback)

    it("should return a CallbackRegistry that does not callbacks for the path passed in") {
      val newRegistry = registry.withoutCallbacksFor(tmpDirPath)
      newRegistry.callbacksFor(tmpDirPath).isEmpty should be(true)
    }

    it(
      "should return a CallbackRegistry without raising an exception even if the path was never registered in the first place") {
      val bareRegistry = CallbackRegistry().withCallbackFor(newTmpDir, callback)
      bareRegistry.withoutCallbacksFor(tmpDirPath).callbacksFor(tmpDirPath).isEmpty should be(true)
      bareRegistry.withoutCallbacksFor(tmpDirPath).callbacksFor(newTmpDir).isEmpty should be(false)
    }
  }

  describe("recursive methods") {

    val tempDirPath       = Files.createTempDirectory("root")
    val tempDirLevel1Path = Files.createTempDirectory(tempDirPath, "level1")
    val tempDirLevel2Path = Files.createTempDirectory(tempDirLevel1Path, "level2")
    val tempFileInTempDir = Files.createTempFile(tempDirPath, "hello", ".there")
    tempDirPath.toFile.deleteOnExit()
    tempDirLevel1Path.toFile.deleteOnExit()
    tempDirLevel2Path.toFile.deleteOnExit()
    tempFileInTempDir.toFile.deleteOnExit()

    describe("#withCallbackForRecursive") {

      val registryWithRecursive =
        CallbackRegistry().withCallbackFor(tempDirPath, dummyFunction, true)

      it("should add callbacks for all folders that exist under the path given") {
        registryWithRecursive
          .callbacksFor(tempDirLevel1Path)
          .map(callbacks => callbacks should contain(dummyFunction))
        registryWithRecursive
          .callbacksFor(tempDirLevel2Path)
          .map(callbacks => callbacks should contain(dummyFunction))
      }

      it("should add callbacks for a file path") {
        val registry = CallbackRegistry().withCallbackFor(tempFileInTempDir, dummyFunction, true)
        registry
          .callbacksFor(tempFileInTempDir)
          .map(callbacks => callbacks should contain(dummyFunction))
      }

      it("should not add callbacks recursively if given a file path") {
        val registry = CallbackRegistry().withCallbackFor(tempFileInTempDir, dummyFunction, true)
        registry
          .callbacksFor(tempFileInTempDir)
          .map(callbacks => callbacks should contain(dummyFunction))
        registry
          .callbacksFor(tempDirLevel1Path)
          .map(callbacks => callbacks should not contain dummyFunction)
        registry
          .callbacksFor(tempDirLevel2Path)
          .map(callbacks => callbacks should not contain dummyFunction)
      }

    }

    describe("#withoutCallbacksForRecursive") {

      it("should cause the callback retrieved for the path via callbacksFor to be empty") {
        val registry                 = CallbackRegistry().withCallbackFor(tempDirPath, dummyFunction, true)
        val registryWithoutCallbacks = registry.withoutCallbacksFor(tempDirPath, true)
        registryWithoutCallbacks.callbacksFor(tempDirPath).isEmpty should be(true)
      }

      it("should case the callback retrieved for directories under a path to be empty") {
        val registry                 = CallbackRegistry().withCallbackFor(tempDirPath, dummyFunction, true)
        val registryWithoutCallbacks = registry.withoutCallbacksFor(tempDirPath, true)
        registryWithoutCallbacks.callbacksFor(tempDirLevel1Path).isEmpty should be(true)
        registryWithoutCallbacks.callbacksFor(tempDirLevel2Path).isEmpty should be(true)
      }

      it("should remove callbacks when given a path to a file") {
        val registry                 = CallbackRegistry().withCallbackFor(tempFileInTempDir, dummyFunction, true)
        val registryWithoutCallbacks = registry.withoutCallbacksFor(tempFileInTempDir, true)
        registryWithoutCallbacks.callbacksFor(tempFileInTempDir).isEmpty should be(true)
      }

    }
  }

  describe("#callbacksFor") {

    val registry   = CallbackRegistry()
    val tmpDirPath = Paths get System.getProperty("java.io.tmpdir")

    it("should be None by default") {
      registry.callbacksFor(tmpDirPath).isEmpty should be(true)
    }

    it("should not be empty for a path once that path has been registered with a callback") {
      val callback = { (path: Path) =>
        println(path.toString)
      }
      val newRegistry = registry.withCallbackFor(tmpDirPath, callback)
      newRegistry.callbacksFor(tmpDirPath).isEmpty should be(false)
    }

    it(
      "should return Some[List[Callback]] that contains the callback that was registered with a path ") {
      val callback = { (path: Path) =>
        println(path.toString)
      }
      val newRegistry = registry.withCallbackFor(tmpDirPath, callback)
      newRegistry
        .callbacksFor(tmpDirPath)
        .map(callbackList => callbackList.contains(callback) should be(true))
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
      val newRegistry = registry
        .withCallbackFor(tmpDirPath, callback1)
        .withCallbackFor(tmpDirPath, callback2)
        .withCallbackFor(tmpDirPath, callback3)
      for {
        callbacks <- newRegistry.callbacksFor(tmpDirPath)
        callback  <- callbacks
      } {
        callback(tmpDirPath)
      }
      sum should be(6)
    }
  }
}
