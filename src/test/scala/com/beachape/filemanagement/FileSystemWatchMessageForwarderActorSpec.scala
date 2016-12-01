package com.beachape.filemanagement

import java.io.{File, PrintWriter}
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds._

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.beachape.filemanagement.Messages.EventAtPath
import com.sun.nio.file.SensitivityWatchEventModifier
import org.scalatest._

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by arunavs on 9/27/16.
  *
  * Test cases for FileSystemWatchMessageForwardingActor.
  */
class FileSystemWatchMessageForwarderActorSpec
    extends TestKit(ActorSystem("FSWatchTest"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEachTestData {

  private val locationDir           = "./src/test/permdata"
  private val locationDirFileHandle = new File(locationDir)
  locationDirFileHandle.deleteOnExit()
  private val allLocs = List(Paths.get("./src/test/permdata"))

  /**
    * Note the passing of the `self` reference, where self
    * is the actor for the test suite itself so we can use expect msg
    */
  val customActorRef = TestActorRef(
    new FileSystemWatchMessageForwardingActor(allLocs,
                                              self,
                                              modifier = Some(SensitivityWatchEventModifier.HIGH)))

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "An FileSystemWatchMessageForwardingActor" must {
    "send a changed Dir Message of modified type file when an existing file is modified" in {
      val existingFileName = "existingFile.txt"
      val existingFile     = new File(locationDir + File.separator + existingFileName)
      printTestToFile(existingFile)
      within(100 seconds) {
        val filePath = existingFile.getAbsoluteFile.toPath
        expectMsg(EventAtPath(ENTRY_MODIFY, filePath))
      }
    }

    "send a changed Dir Message of created type file when a new file is created" in {
      val newFileName = "newFile.txt"
      val newFile     = new File(locationDir + File.separator + newFileName)
      newFile.createNewFile()
      newFile.deleteOnExit()
      within(100 seconds) {
        val filePath = newFile.getAbsoluteFile.toPath
        expectMsg(EventAtPath(ENTRY_CREATE, filePath))
      }
    }
  }

  def printTestToFile(file: File): Unit = {
    val pw = new PrintWriter(file)
    pw.write("Hello, world")
    pw.close()
  }
}
