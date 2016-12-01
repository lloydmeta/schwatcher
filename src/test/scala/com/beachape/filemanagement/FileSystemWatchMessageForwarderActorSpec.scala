package com.beachape.filemanagement

import java.io.{File, PrintWriter}
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds._

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.beachape.filemanagement.Messages.EventAtPath
import com.sun.nio.file.SensitivityWatchEventModifier
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Nanoseconds, Seconds, Span}

/**
  * Created by arunavs on 9/27/16.
  *
  * Test cases for FileSystemWatchMessageForwardingActor.
  */
class FileSystemWatchMessageForwarderActorSpec extends FunSpec with Matchers with Eventually {

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(100, Seconds)), interval = scaled(Span(5, Nanoseconds)))

  // Use Fixtures just to avoid dealing with expectMsg dying on lagging messages across tests
  sealed abstract class Fixtures extends TestKit(ActorSystem("testSystem")) with ImplicitSender {

    protected val locationDir = "./src/test/permdata"
    val newFileName           = "newFile.txt"
    val newFile               = new File(locationDir + File.separator + newFileName)
    newFile.delete()

    private val locationDirFileHandle = new File(locationDir)
    locationDirFileHandle.deleteOnExit()
    private val allLocs = List(Paths.get("./src/test/permdata"))

    /**
      * Note the passing of the `self` reference, where self
      * is the actor for the test suite itself so we can use expect msg
      */
    val customActorRef = TestActorRef(
      new FileSystemWatchMessageForwardingActor(
        allLocs,
        self,
        modifier = Some(SensitivityWatchEventModifier.HIGH)))

  }

  it("should send a changed Dir Message of modified type file when an existing file is modified") {
    new Fixtures {
      val existingFileName = "existingFile.txt"
      val existingFile     = new File(locationDir + File.separator + existingFileName)
      printTestToFile(existingFile)
      eventually {
        val filePath = existingFile.getAbsoluteFile.toPath
        expectMsg(EventAtPath(ENTRY_MODIFY, filePath))
      }
    }
  }

  it("should send a changed Dir Message of created type file when a new file is created") {
    new Fixtures {
      newFile.createNewFile()
      newFile.deleteOnExit()
      eventually {
        val filePath = newFile.getAbsoluteFile.toPath
        expectMsg(EventAtPath(ENTRY_CREATE, filePath))
      }
    }
  }

  it("should send a changed Dir Message of created type file when a new file is deleted") {
    new Fixtures {
      newFile.createNewFile()
      Thread.sleep(10000)
      newFile.delete()
      eventually {
        val filePath = newFile.getAbsoluteFile.toPath
        expectMsg(EventAtPath(ENTRY_DELETE, filePath))
      }
    }
  }

  def printTestToFile(file: File): Unit = {
    val pw = new PrintWriter(file)
    pw.write("Hello, world")
    pw.close()
  }
}
