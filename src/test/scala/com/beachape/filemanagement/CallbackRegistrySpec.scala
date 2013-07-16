package com.beachape.filemanagement

import org.scalatest.BeforeAndAfter
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

import java.nio.file.StandardWatchEventKinds._

class CallbackRegistrySpec extends FunSpec with ShouldMatchers with BeforeAndAfter {

  describe("companion factory object") {

    it("should create an instance of CallbackRegistry") {
      CallbackRegistry(ENTRY_CREATE).isInstanceOf[CallbackRegistry] should be(true)
    }

    it("should create a CallbackRegistry with the same event type passed to the factory") {
      CallbackRegistry(ENTRY_CREATE).eventType should be(ENTRY_CREATE)
    }
  }
}
