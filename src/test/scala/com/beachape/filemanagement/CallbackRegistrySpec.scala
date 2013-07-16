package com.beachape.filemanagement

import org.scalatest.{PrivateMethodTester, BeforeAndAfter, FunSpec}
import org.scalatest.matchers.ShouldMatchers

import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{Path, Paths}

class CallbackRegistrySpec extends FunSpec
with PrivateMethodTester
with ShouldMatchers
with BeforeAndAfter {

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

    it("should create a new CallbackRegistry") {
      registry.withPathCallback(tmpDirPath, {
        path =>
      }).isInstanceOf[CallbackRegistry] should be(true)
    }

    it("should create a new CallbackRegistry that has the proper callback registered for the given path") {
      val callback = {
        (path: Path) => println(path.toString)
      }
      val newRegistry = registry.withPathCallback(tmpDirPath, callback)
      newRegistry.callbacksForPath(tmpDirPath).map(callbackList => callbackList.contains(callback) should be(true))
    }

    it("should not affect the old CallbackRegistry") {
      val callback = {
        (path: Path) => println(path.toString)
      }
      registry.withPathCallback(tmpDirPath, callback)
      registry.callbacksForPath(tmpDirPath).map(callbackList => callbackList.contains(callback) should be(false))
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
  }
}
