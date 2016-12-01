package com.beachape.filemanagement

import java.nio.file.Path
import java.nio.file.WatchEvent.Kind

import com.beachape.filemanagement.Messages.EventAtPath

import scala.concurrent.duration.FiniteDuration

/**
  * Created by Lloyd on 11/25/15.
  */
private[filemanagement] case class Occurrence(event: Kind[_], path: Path, storedAt: Long)

object EventTracker {

  private[filemanagement] def eventToOccurrence(eventAtPath: EventAtPath): Occurrence = {
    val newPath = eventAtPath.path
    Occurrence(
      event = eventAtPath.event,
      path = newPath,
      storedAt = System.currentTimeMillis()
    )
  }

  private[filemanagement] def occurrenceToEvent(occurrence: Occurrence): EventAtPath = {
    EventAtPath(occurrence.event, occurrence.path)
  }

}

private[filemanagement] case class EventTracker(occurrences: Seq[Occurrence] = Nil) {

  import EventTracker._

  /**
    * Given an EventAtPath, checks to see if:
    *
    * - There already exists an occurrence with the same event, path
    *
    * Any existing occurrences that meet the above two criteria will be popped off and returned
    * in the first element of the pair as an [[EventAtPath]]
    *
    * If no elements meeting the aforementioned criteria are found, a copy of the current tracker
    * will be returned with the occurrence recorded
    */
  def popExistingOrAdd(incomingEvent: EventAtPath): (Seq[EventAtPath], EventTracker) = {
    val (popped, keep) = occurrences.partition { occ =>
      occ.event == incomingEvent.event && occ.path == incomingEvent.path
    }
    if (popped.isEmpty)
      (Nil, this.copy(occurrences :+ eventToOccurrence(incomingEvent)))
    else
      (popped.map(occurrenceToEvent), EventTracker(keep))
  }

  /**
    * Pops the occurrences older than the span given, returning the popped
    * occurrences and a copy of this tracker without the popped elements in a
    * pair
    */
  def popOlderThan(span: FiniteDuration): (Seq[EventAtPath], EventTracker) = {
    val keepFrom = System.currentTimeMillis() - span.toMillis
    val (popped, keep) = occurrences.partition { occ =>
      occ.storedAt < keepFrom
    }
    (popped.map(occurrenceToEvent), EventTracker(keep))
  }

}
