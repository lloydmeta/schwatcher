package com.beachape.filemanagement

import java.nio.file.Files
import java.nio.file.StandardWatchEventKinds._
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import collection.JavaConversions._

import akka.testkit.TestActorRef
import akka.actor.{ActorSystem, Actor}
import akka.testkit.TestKit
import java.io.{FileWriter, BufferedWriter}

class DummyActor extends Actor {
  def receive = {
    case _ =>
  }
}

class WatchServiceTaskSpec extends TestKit(ActorSystem("testSystem"))
  with FunSpec
  with ShouldMatchers
  with BeforeAndAfter {

  val dummyActor = TestActorRef[DummyActor]
  var watchServiceTask = WatchServiceTask(dummyActor)

  val tempDirPath = Files.createTempDirectory("root")
  val tempFileInTempDir = Files.createTempFile(tempDirPath, "hello", ".there")

  before{
    watchServiceTask = WatchServiceTask(dummyActor)
  }

  describe("#watch") {

    describe("for ENTRY_CREATE") {

      it("should cause ENTRY_CREATE events to be detectable for a directory path") {
        val Some(watchKey) = watchServiceTask.watch(tempDirPath, ENTRY_CREATE)
        Files.createTempFile(tempDirPath, "hello", ".there2")
        var eventList = watchKey.pollEvents
        while (eventList.isEmpty ){
          eventList = watchKey.pollEvents()
          Thread.sleep(100)
        }
        eventList foreach {_.kind() should be(ENTRY_CREATE)}
      }

      it("should cause ENTRY_CREATE events to be detectable for a file path") {
        val Some(watchKey) = watchServiceTask.watch(tempFileInTempDir, ENTRY_CREATE)
        Files.createTempFile(tempDirPath, "hello", ".there2")
        var eventList = watchKey.pollEvents
        while (eventList.isEmpty ){
          eventList = watchKey.pollEvents()
          Thread.sleep(100)
        }
        eventList foreach {_.kind() should be(ENTRY_CREATE)}
      }

    }

    describe("for ENTRY_DELETE") {

      it("should cause ENTRY_DELETE events to be detectable for a directory path") {
        val file = Files.createTempFile(tempDirPath, "hello", ".there3")
        val Some(watchKey) = watchServiceTask.watch(tempDirPath, ENTRY_DELETE)
        file.toFile.delete()
        var eventList = watchKey.pollEvents()
        while (eventList.isEmpty ){
          eventList = watchKey.pollEvents()
          Thread.sleep(100)
        }
        eventList foreach {_.kind() should be(ENTRY_DELETE)}
      }

      it("should cause ENTRY_DELETE events to be detectable for a file path") {
        val file = Files.createTempFile(tempDirPath, "hello", ".there3")
        val Some(watchKey) = watchServiceTask.watch(file, ENTRY_DELETE)
        file.toFile.delete()
        var eventList = watchKey.pollEvents()
        while (eventList.isEmpty ){
          eventList = watchKey.pollEvents()
          Thread.sleep(100)
        }
        eventList foreach {_.kind() should be(ENTRY_DELETE)}
      }

    }

    describe("for ENTRY_MODIFY") {

      it("should cause ENTRY_MODIFY events to be detectable for a directory path") {
        val Some(watchKey) = watchServiceTask.watch(tempDirPath, ENTRY_MODIFY)
        val writer = new BufferedWriter(new FileWriter(tempFileInTempDir.toFile))
        writer.write(
          """
            |Theres text in here !!
          """)
        writer.close
        var eventList = watchKey.pollEvents()
        while (eventList.isEmpty ){
          eventList = watchKey.pollEvents()
          Thread.sleep(100)
        }
        eventList foreach {_.kind() should be(ENTRY_MODIFY)}
      }

      it("should cause ENTRY_DELETE events to be detectable for a file path") {
        val Some(watchKey) = watchServiceTask.watch(tempFileInTempDir, ENTRY_MODIFY)
        val writer = new BufferedWriter(new FileWriter(tempFileInTempDir.toFile))
        writer.write(
          """
            |Theres text in here !!
          """)
        writer.close
        var eventList = watchKey.pollEvents()
        while (eventList.isEmpty ){
          eventList = watchKey.pollEvents()
          Thread.sleep(100)
        }
        eventList foreach {_.kind() should be(ENTRY_MODIFY)}
      }
    }

  }

}
