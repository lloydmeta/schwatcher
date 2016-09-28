package com.beachape.filemanagement

import java.io.{ File, PrintWriter }

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import com.beachape.FileSystemWatchActor
import com.beachape.FileSystemWatchActor.ChangedDir
import org.scalatest._

import scala.concurrent.duration._

/**
 * Created by arunavs on 9/27/16.
 *
 * Test casses for FileSystemWatchActor
 */

class FileSystemWatchActorSpec extends TestKit(ActorSystem("FSWatchTest")) with ImplicitSender
    with WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEachTestData {

  val locationDir = "./src/test/permdata"
  val locationDirFileHandle = new File(locationDir)
  locationDirFileHandle.deleteOnExit()
  val allLocs = List("./src/test/permdata")

  /**
   * Note the passing of the `self` reference, where self
   * is the actor for the test suite itself so we can use expect msg
   */
  val customActorRef = TestActorRef(new FileSystemWatchActor(allLocs, self))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "An FSWatchImproved actor" must {
    "send a changed Dir Message" in {
      val newFileName = "newFile.txt"
      val newFile = new File(locationDir + File.separator + newFileName)
      printTestToFile(newFile)
      within(100 seconds) {
        val filePath = newFile.getCanonicalFile.toPath
        expectMsg(ChangedDir(filePath))
      }
    }
  }

  def printTestToFile(file: File): Unit = {
    val pw = new PrintWriter(file)
    pw.write("Hello, world")
    pw.close()
  }
}
