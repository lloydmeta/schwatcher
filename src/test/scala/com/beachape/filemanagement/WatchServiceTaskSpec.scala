package com.beachape.filemanagement

import akka.actor.{ActorSystem, Actor}
import akka.testkit.{TestActorRef, TestKit}
import scala.collection.JavaConverters._
import java.io.{FileWriter, BufferedWriter}
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{Path, Files}
import org.scalatest._
import scala.concurrent.duration._
import scala.language.postfixOps

class DummyActor extends Actor {
  def receive = {
    case _ =>
  }
}

class WatchServiceTaskSpec
    extends TestKit(ActorSystem("testSystem"))
    with FunSpecLike
    with Matchers
    with BeforeAndAfter {

  val dummyActor       = TestActorRef[DummyActor]
  var watchServiceTask = WatchServiceTask(dummyActor)

  val tempDirPath       = Files.createTempDirectory("root")
  val tempFileInTempDir = Files.createTempFile(tempDirPath.toAbsolutePath, "hello", ".there")
  tempDirPath.toFile.deleteOnExit()
  tempFileInTempDir.toFile.deleteOnExit()

  before {
    watchServiceTask = WatchServiceTask(dummyActor)
  }

  after {
    watchServiceTask.stopService()
  }

  private def repeatFor(duration: Duration, orUntil: => Boolean = false)(f: => Unit): Unit = {
    var timeRemaining = duration
    while (!orUntil && timeRemaining.toMillis > 0) {
      f
      Thread.sleep(50)
      timeRemaining = timeRemaining - (50 millis)
    }
  }

  describe("#watch") {

    describe("for ENTRY_CREATE") {

      it("should cause ENTRY_CREATE events to be detectable for a directory path") {
        val Some(watchKey) = watchServiceTask.watch(tempDirPath, None, ENTRY_CREATE)
        Files.createTempFile(tempDirPath, "hello", ".there2").toFile.deleteOnExit()
        val eventList = watchKey.pollEvents().asScala
        repeatFor(30 seconds, eventList.nonEmpty) {
          eventList.append(watchKey.pollEvents().asScala: _*)
        }
        watchKey.reset()
        eventList foreach { _.kind() should be(ENTRY_CREATE) }
      }

      it("should cause ENTRY_CREATE events to be detectable for a file path") {
        val Some(watchKey) = watchServiceTask.watch(tempFileInTempDir, None, ENTRY_CREATE)
        Files.createTempFile(tempDirPath, "hello", ".there2").toFile.deleteOnExit()
        val eventList = watchKey.pollEvents().asScala
        repeatFor(30 seconds, eventList.nonEmpty) {
          eventList.append(watchKey.pollEvents().asScala: _*)
        }
        watchKey.reset()
        eventList foreach { _.kind() should be(ENTRY_CREATE) }
      }

      describe("implicit testing of #contextAbsolutePath") {

        it("should have the correct path in the event") {
          val Some(watchKey) = watchServiceTask.watch(tempDirPath, None, ENTRY_CREATE)
          val fileCreated    = Files.createTempFile(tempDirPath, "hello", ".there2")
          fileCreated.toFile.deleteOnExit()
          val eventList = watchKey.pollEvents().asScala
          repeatFor(30 seconds, eventList.nonEmpty) {
            eventList.append(watchKey.pollEvents().asScala: _*)
          }
          watchKey.reset()
          eventList foreach { event =>
            watchServiceTask
              .contextAbsolutePath(watchKey, event.context().asInstanceOf[Path]) should
              be(fileCreated.toAbsolutePath)
          }
        }

      }

    }

    describe("for ENTRY_DELETE") {

      it("should cause ENTRY_DELETE events to be detectable for a directory path") {
        val fileToDelete   = Files.createTempFile(tempDirPath, "test", ".file")
        val Some(watchKey) = watchServiceTask.watch(tempDirPath, None, ENTRY_DELETE)
        fileToDelete.toFile.delete()
        val eventList = watchKey.pollEvents().asScala
        repeatFor(30 seconds, eventList.nonEmpty) {
          eventList.append(watchKey.pollEvents().asScala: _*)
        }
        watchKey.reset()
        eventList foreach { _.kind() should be(ENTRY_DELETE) }
      }

      it("should cause ENTRY_DELETE events to be detectable for a file path") {
        val fileToDelete   = Files.createTempFile(tempDirPath, "test", ".file")
        val Some(watchKey) = watchServiceTask.watch(fileToDelete, None, ENTRY_DELETE)
        fileToDelete.toFile.delete()
        val eventList = watchKey.pollEvents().asScala
        repeatFor(30 seconds, eventList.nonEmpty) {
          eventList.append(watchKey.pollEvents().asScala: _*)
        }
        watchKey.reset()
        eventList foreach { _.kind() should be(ENTRY_DELETE) }
      }

      describe("implicit testing of #contextAbsolutePath") {

        it("should have the correct path in the event") {
          val fileToDelete   = Files.createTempFile(tempDirPath, "test", ".file")
          val Some(watchKey) = watchServiceTask.watch(tempDirPath, None, ENTRY_DELETE)
          fileToDelete.toFile.delete()
          val eventList = watchKey.pollEvents().asScala
          repeatFor(30 seconds, eventList.nonEmpty) {
            eventList.append(watchKey.pollEvents().asScala: _*)
          }
          watchKey.reset()
          eventList foreach { event =>
            watchServiceTask
              .contextAbsolutePath(watchKey, event.context().asInstanceOf[Path]) should
              be(fileToDelete.toAbsolutePath)
          }
        }

      }

    }

    describe("for ENTRY_MODIFY") {

      it("should cause ENTRY_MODIFY events to be detectable for a directory path") {
        val Some(watchKey) = watchServiceTask.watch(tempDirPath, None, ENTRY_MODIFY)
        val writer         = new BufferedWriter(new FileWriter(tempFileInTempDir.toFile))
        writer.write(
          """
            |Theres text in here !!
          """
        )
        writer.close()
        val eventList = watchKey.pollEvents().asScala
        repeatFor(30 seconds, eventList.nonEmpty) {
          eventList.append(watchKey.pollEvents().asScala: _*)
        }
        watchKey.reset()
        eventList foreach { _.kind() should be(ENTRY_MODIFY) }
      }

      it("should cause ENTRY_MODIFY events to be detectable for a file path") {
        val Some(watchKey) = watchServiceTask.watch(tempFileInTempDir, None, ENTRY_MODIFY)
        val writer         = new BufferedWriter(new FileWriter(tempFileInTempDir.toFile))
        writer.write(
          """
            |Theres text in here again!!
          """
        )
        writer.close()
        val eventList = watchKey.pollEvents().asScala
        repeatFor(30 seconds, eventList.nonEmpty) {
          eventList.append(watchKey.pollEvents().asScala: _*)
        }
        watchKey.reset()
        eventList foreach { _.kind() should be(ENTRY_MODIFY) }
      }

      describe("implicit testing of #contextAbsolutePath") {

        it("should have the correct path in the event") {
          val Some(watchKey) = watchServiceTask.watch(tempFileInTempDir, None, ENTRY_MODIFY)
          val writer         = new BufferedWriter(new FileWriter(tempFileInTempDir.toFile))
          writer.write(
            """
              |Theres text in here wee!!
            """
          )
          writer.close()
          val eventList = watchKey.pollEvents().asScala
          repeatFor(30 seconds, eventList.nonEmpty) {
            eventList.append(watchKey.pollEvents().asScala: _*)
          }
          watchKey.reset()
          eventList foreach { event =>
            watchServiceTask
              .contextAbsolutePath(watchKey, event.context().asInstanceOf[Path]) should
              be(tempFileInTempDir.toAbsolutePath)
          }
        }

      }

    }

    describe("for multiple WatchKind.Events") {

      it("should cause all registered event kinds to be detectable for a path") {
        val tempFileInTempDir2 =
          Files.createTempFile(tempDirPath.toAbsolutePath, "hello2", ".there")
        val Some(watchKey) =
          watchServiceTask.watch(tempFileInTempDir2, None, ENTRY_MODIFY, ENTRY_DELETE)
        val writer = new BufferedWriter(new FileWriter(tempFileInTempDir2.toFile))
        writer.write(
          """
            |Theres text in here !!
          """
        )
        writer.close()
        val eventList = watchKey.pollEvents().asScala
        /*
         .size >= 2 because some systems fire multiple entry modifies on update
         */
        repeatFor(30 seconds, eventList.size >= 2) {
          eventList.append(watchKey.pollEvents().asScala: _*)
        }
        watchKey.reset()
        eventList foreach { _.kind() should be(ENTRY_MODIFY) }
        tempFileInTempDir2.toFile.delete()
        val eventList2 = watchKey.pollEvents().asScala
        repeatFor(30 seconds, eventList2.nonEmpty) {
          eventList2.append(watchKey.pollEvents().asScala: _*)
        }
        watchKey.reset()
        /*
          We don't dedupe entry modify events on the WatchServiceTask level
          So we need do a contains check in case there is a stray entry modify
          event
         */
        eventList2 foreach { _.kind() should be(ENTRY_DELETE) }
      }
    }

  }

}
