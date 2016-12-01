package com.beachape.filemanagement

import com.beachape.filemanagement.Messages.EventAtPath
import org.scalatest.{Matchers, FunSpec}
import java.nio.file.StandardWatchEventKinds._

import scala.concurrent.duration._

/**
  * Created by Lloyd on 11/25/15.
  */
class EventTrackerSpec extends FunSpec with Matchers {

  trait Fixture {
    val tempFile     = java.io.File.createTempFile("fakeFile", ".log")
    val tempFilePath = tempFile.toPath
    tempFile.deleteOnExit()
    val modifyEventAtPath = EventAtPath(ENTRY_MODIFY, tempFilePath)
    val modifyOccurrence  = EventTracker.eventToOccurrence(modifyEventAtPath)
    val deleteEventAtPath = EventAtPath(ENTRY_DELETE, tempFilePath)
    val deleteOccurrence  = EventTracker.eventToOccurrence(deleteEventAtPath)
  }

  describe("#popExistingOrAdd") {

    it("should add if the tracker is empty") {
      new Fixture {
        val empty           = EventTracker()
        val (popped, added) = empty.popExistingOrAdd(EventAtPath(ENTRY_MODIFY, tempFilePath))
        popped shouldBe 'empty
        added.occurrences.size shouldBe 1
      }
    }

    it("should add if there are no occurrences with the same kind and path") {
      new Fixture {
        val empty           = EventTracker(Seq(deleteOccurrence))
        val (popped, added) = empty.popExistingOrAdd(EventAtPath(ENTRY_MODIFY, tempFilePath))
        popped shouldBe 'empty
        added.occurrences.size shouldBe 2
      }
    }

    it("should not add if there was already an occurrence with the same kind and path") {
      new Fixture {
        val hasEvent        = EventTracker(Seq(modifyOccurrence))
        val (popped, added) = hasEvent.popExistingOrAdd(modifyEventAtPath)
        popped.size shouldBe 1
        popped should contain(modifyEventAtPath)
        added.occurrences.size shouldBe 0
      }
    }

    it("should pop all occurrences with the same event and path when trying to add") {
      new Fixture {
        val hasEvent        = EventTracker(Seq(modifyOccurrence, modifyOccurrence, modifyOccurrence))
        val (popped, added) = hasEvent.popExistingOrAdd(modifyEventAtPath)
        popped.size shouldBe 3
        popped should contain(modifyEventAtPath)
        added.occurrences.size shouldBe 0
      }
    }

  }

  describe("popOlderThan") {

    it("should return nothing if the tracker is empty") {
      val (popped, _) = EventTracker().popOlderThan(0.seconds)
      popped shouldBe 'empty
    }

    it(
      "should pop everything that was added *before* the current time minuse the provided duration") {
      new Fixture {
        val t = EventTracker(Seq(modifyOccurrence))
        Thread.sleep(5.millis.toMillis)
        val (_, added)        = t.popExistingOrAdd(deleteEventAtPath)
        val (popped, tracker) = added.popOlderThan(3.millis)
        popped.size shouldBe 1
        popped should contain(modifyEventAtPath)
        tracker.occurrences.size shouldBe 1
        tracker.occurrences.map(EventTracker.occurrenceToEvent) should contain(deleteEventAtPath)
      }
    }

  }

}
