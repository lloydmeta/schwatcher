package com.beachape.filemanagement

import java.nio.file.{Path, Files}
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

  var tempDirPath = Files.createTempDirectory("root")
  var tempFileInTempDir = Files.createTempFile(tempDirPath.toAbsolutePath, "hello", ".there")
  tempDirPath.toFile.deleteOnExit()

  before {
    watchServiceTask = WatchServiceTask(dummyActor)
    tempFileInTempDir = Files.createTempFile(tempDirPath.toAbsolutePath, "hello", ".there")
    tempFileInTempDir.toFile.deleteOnExit()
  }

  describe("#watch") {

    describe("for ENTRY_CREATE") {

      it("should cause ENTRY_CREATE events to be detectable for a directory path") {
        val Some(watchKey) = watchServiceTask.watch(tempDirPath, ENTRY_CREATE)
        Files.createTempFile(tempDirPath.toAbsolutePath, "hello", ".there2")
        var eventList = watchKey.pollEvents
        while (eventList.isEmpty ){
          eventList = watchKey.pollEvents()
          Thread.sleep(100)
        }
        eventList foreach {_.kind() should be(ENTRY_CREATE)}
      }

      it("should cause ENTRY_CREATE events to be detectable for a file path") {
        val Some(watchKey) = watchServiceTask.watch(tempFileInTempDir, ENTRY_CREATE)
        Files.createTempFile(tempDirPath.toAbsolutePath, "hello", ".there2")
        var eventList = watchKey.pollEvents
        while (eventList.isEmpty ){
          eventList = watchKey.pollEvents()
          Thread.sleep(100)
        }
        eventList foreach {_.kind() should be(ENTRY_CREATE)}
      }

      it("should have the correct path in the event") {
        val Some(watchKey) = watchServiceTask.watch(tempDirPath, ENTRY_CREATE)
        val fileCreated = Files.createTempFile(tempDirPath.toAbsolutePath, "hello", ".there2")
        var eventList = watchKey.pollEvents
        while (eventList.isEmpty ){
          eventList = watchKey.pollEvents()
          Thread.sleep(100)
        }
        println(tempDirPath.toAbsolutePath)
        println(tempDirPath.toRealPath())
        println("created file path")
        println(fileCreated.toRealPath().toAbsolutePath)
        println(fileCreated.toFile.getAbsolutePath)
        println("created file path..")
        eventList foreach {_.context().asInstanceOf[Path].toRealPath() should be(fileCreated.toRealPath}
      }

    }

    describe("for ENTRY_DELETE") {

      it("should cause ENTRY_DELETE events to be detectable for a directory path") {
        val Some(watchKey) = watchServiceTask.watch(tempDirPath, ENTRY_DELETE)
        tempFileInTempDir.toFile.delete()
        var eventList = watchKey.pollEvents()
        while (eventList.isEmpty ){
          eventList = watchKey.pollEvents()
          Thread.sleep(100)
        }
        eventList foreach {_.kind() should be(ENTRY_DELETE)}
      }

      it("should cause ENTRY_DELETE events to be detectable for a file path") {
        val Some(watchKey) = watchServiceTask.watch(tempFileInTempDir, ENTRY_DELETE)
        tempFileInTempDir.toFile.delete()
        var eventList = watchKey.pollEvents()
        while (eventList.isEmpty ){
          eventList = watchKey.pollEvents()
          Thread.sleep(100)
        }
        eventList foreach {_.kind() should be(ENTRY_DELETE)}
      }

      it("should have the correct path in the event") {
        val Some(watchKey) = watchServiceTask.watch(tempDirPath, ENTRY_DELETE)
        tempFileInTempDir.toFile.delete()
        var eventList = watchKey.pollEvents()
        while (eventList.isEmpty ){
          eventList = watchKey.pollEvents()
          Thread.sleep(100)
        }
        eventList foreach {_.context().asInstanceOf[Path].toAbsolutePath should be(tempFileInTempDir.toAbsolutePath)}
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

      it("should cause ENTRY_MODIFY events to be detectable for a file path") {
        val Some(watchKey) = watchServiceTask.watch(tempFileInTempDir, ENTRY_MODIFY)
        val writer = new BufferedWriter(new FileWriter(tempFileInTempDir.toFile))
        writer.write(
          """
            |Theres text in here again !!
          """)
        writer.close
        var eventList = watchKey.pollEvents()
        while (eventList.isEmpty ){
          eventList = watchKey.pollEvents()
          Thread.sleep(100)
        }
        eventList foreach {_.kind() should be(ENTRY_MODIFY)}
      }

      it("should have the correct path in the event") {
        val Some(watchKey) = watchServiceTask.watch(tempFileInTempDir, ENTRY_MODIFY)
        val writer = new BufferedWriter(new FileWriter(tempFileInTempDir.toFile))
        writer.write(
          """
            |Theres text in here wee!!
          """)
        writer.close
        var eventList = watchKey.pollEvents()
        while (eventList.isEmpty ){
          eventList = watchKey.pollEvents()
          Thread.sleep(100)
        }
        eventList foreach {_.context().asInstanceOf[Path].toAbsolutePath should be(tempFileInTempDir.toAbsolutePath)}
      }

    }

  }

}
