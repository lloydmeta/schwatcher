package com.beachape.filemanagement

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import com.beachape.filemanagement.Messages._
import com.beachape.filemanagement.RegistryTypes.Callback
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{ Files, Path, WatchEvent }
import java.nio.file.WatchEvent.Modifier
import org.scalatest._
import java.io.{ File, FileWriter, BufferedWriter }
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.language.postfixOps
import com.beachape.filemanagement.MonitorActor.CallbackRegistryMap

class MonitorActorSpec extends TestKit(ActorSystem("testSystem"))
    with FunSpecLike
    with Matchers
    with BeforeAndAfter
    with ImplicitSender
    with PrivateMethodTester {

  trait Fixtures {
    val emptyCbMap = MonitorActor.initialEventTypeCallbackRegistryMap

    // Actor
    val monitorActorRef = TestActorRef(new MonitorActor)
    val monitorActor = monitorActorRef.underlyingActor

    // Files
    val tempFile = java.io.File.createTempFile("fakeFile", ".log")
    val tempDirPath = Files.createTempDirectory("root")
    val tempDirLevel1Path = Files.createTempDirectory(tempDirPath, "level1")
    val tempDirLevel2Path = Files.createTempDirectory(tempDirLevel1Path, "level2")
    val tempFileInTempDir = Files.createTempFile(tempDirPath, "hello", ".there")

    // Make sure the files get deleted on exit
    tempFile.deleteOnExit()
    tempDirPath.toFile.deleteOnExit()
    tempDirLevel1Path.toFile.deleteOnExit()
    tempDirLevel2Path.toFile.deleteOnExit()
    tempFileInTempDir.toFile.deleteOnExit()

    val dummyFunction: Path => Unit = {
      (path: Path) => val bleh = "lala"
    }
    val newRegistryMap = PrivateMethod[CallbackRegistryMap]('newCallbackRegistryMap)

    val HIGH = get_com_sun_nio_file_SensitivityWatchEventModifier("HIGH")
    val LOW = get_com_sun_nio_file_SensitivityWatchEventModifier("LOW")

    // Test helper methods
    def addCallbackFor(
      originalMap: CallbackRegistryMap,
      path: Path,
      event: WatchEvent.Kind[Path],
      callback: Callback,
      recursive: Boolean = false
    ): CallbackRegistryMap = {
      val absolutePath = path.toAbsolutePath
      monitorActor invokePrivate newRegistryMap(
        originalMap,
        event, {
        registry: CallbackRegistry => registry.withCallbackFor(absolutePath, callback, recursive)
      }
      )
    }

    def removeCallbacksFor(
      originalMap: CallbackRegistryMap,
      path: Path,
      event: WatchEvent.Kind[Path],
      recursive: Boolean = false
    ): CallbackRegistryMap = {
      val absolutePath = path.toAbsolutePath
      monitorActor invokePrivate newRegistryMap(
        originalMap,
        event, {
        registry: CallbackRegistry => registry.withoutCallbacksFor(absolutePath, recursive)
      }
      )
    }

    // Recursive version of the same as above
    def addCallbackFor(
      originalMap: CallbackRegistryMap,
      pathsCallbackRecursive: List[(Path, WatchEvent.Kind[Path], Callback, Boolean)]
    ): CallbackRegistryMap = {
      pathsCallbackRecursive.foldLeft(originalMap) {
        case (memo, (path, eventType, callback, recursive)) => addCallbackFor(memo, path, eventType, callback, recursive)
      }
    }

    // Load `com.sun.nio.file.SensitivityWatchEventModifie._` if possible
    private def get_com_sun_nio_file_SensitivityWatchEventModifier(field: String): Option[Modifier] = {
      try {
        val c = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier")
        val f = c.getField(field)
        val m = f.get(c).asInstanceOf[Modifier]
        Some(m)
      } catch {
        case NonFatal(e) =>
          None
      }
    }

  }

  trait MessagingFixtures extends Fixtures {
    val callbackFunc = {
      (path: Path) => testActor ! path
    }
  }

  describe("construction via Props factory") {

    it("should throw an error when concurrency parameter is set to less than 1") {
      val thrown = intercept[IllegalArgumentException] {
        TestActorRef(MonitorActor(0))
      }
      thrown.getMessage should be("requirement failed: Callback concurrency requested is 0 but it should at least be 1")
    }

    it("should not throw an error when concurrency parameter is set to 1") {
      TestActorRef(MonitorActor(1))
      true should be(true)
    }

    it("should not throw an error when concurrency parameter is set to > 1") {
      TestActorRef(MonitorActor(2))
      true should be(true)
    }

  }

  describe("methods testing") {

    describe("#modifyCallbackRegistry") {

      it("should allow callbacks to be added to the registry") {
        new Fixtures {
          val map = addCallbackFor(emptyCbMap, tempFile.toPath, ENTRY_CREATE, dummyFunction)
          monitorActor.callbacksFor(map, ENTRY_CREATE, tempFile.toPath).isEmpty should be(false)
          monitorActor.callbacksFor(map, ENTRY_CREATE, tempFile.toPath) foreach {
            callbacks =>
              callbacks should contain(dummyFunction)
          }
        }
      }

      it("should allow callbacks to be removed from the registry") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempFile.toPath, ENTRY_CREATE, dummyFunction)
          val removedMap = removeCallbacksFor(addedMap, tempFile.toPath, ENTRY_CREATE)
          monitorActor.callbacksFor(removedMap, ENTRY_CREATE, tempFile.toPath).isEmpty should be(true)
        }
      }
    }

    describe("adding callbacks recursively") {

      it("should add callbacks for all folders that exist under the path given") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempDirPath, ENTRY_CREATE, dummyFunction, true)
          monitorActor.callbacksFor(addedMap, ENTRY_CREATE, tempDirLevel1Path).map(callbacks =>
            callbacks should contain(dummyFunction))
          monitorActor.callbacksFor(addedMap, ENTRY_CREATE, tempDirLevel2Path).map(callbacks =>
            callbacks should contain(dummyFunction))
        }
      }

      it("should add callbacks for a file path") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempFileInTempDir, ENTRY_CREATE, dummyFunction, true)
          monitorActor.callbacksFor(addedMap, ENTRY_CREATE, tempFileInTempDir).map(callbacks =>
            callbacks should contain(dummyFunction))
        }
      }

      it("should not add callbacks recursively if given a file path") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempFileInTempDir, ENTRY_CREATE, dummyFunction, true)
          monitorActor.callbacksFor(addedMap, ENTRY_CREATE, tempDirLevel1Path).map(callbacks =>
            callbacks should not contain dummyFunction)
          monitorActor.callbacksFor(addedMap, ENTRY_CREATE, tempDirLevel2Path).map(callbacks =>
            callbacks should not contain dummyFunction)
        }
      }

    }

    describe("recursively removing callbacks") {

      it("should remove callbacks for all folders that exist under the path given") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempDirPath, ENTRY_CREATE, dummyFunction, true)
          val removedMap = removeCallbacksFor(addedMap, tempDirPath, ENTRY_CREATE, true)
          monitorActor.callbacksFor(removedMap, ENTRY_CREATE, tempDirLevel1Path).isEmpty should be(true)
          monitorActor.callbacksFor(removedMap, ENTRY_CREATE, tempDirLevel2Path).isEmpty should be(true)
        }
      }

      it("should remove callbacks for a file path") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempFileInTempDir, ENTRY_CREATE, dummyFunction, true)
          val removedMap = removeCallbacksFor(addedMap, tempFileInTempDir, ENTRY_CREATE, true)
          monitorActor.callbacksFor(removedMap, ENTRY_CREATE, tempFileInTempDir).isEmpty should be(true)
        }
      }

    }

    describe("#callbacksFor") {

      it("should return Some[Callbacks] that contains prior registered callbacks for a path") {
        new Fixtures {
          val cbMap = addCallbackFor(emptyCbMap, tempFile.toPath, ENTRY_CREATE, dummyFunction)
          monitorActor.callbacksFor(cbMap, ENTRY_CREATE, tempFile.toPath) foreach {
            callbacks =>
              callbacks should contain(dummyFunction)
          }
        }
      }

      it("should return Some[Callbacks] that does not contain callbacks for paths never registered") {
        new Fixtures {
          val tempFile2 = java.io.File.createTempFile("fakeFile2", ".log")
          tempFile2.deleteOnExit()
          monitorActor.callbacksFor(emptyCbMap, ENTRY_CREATE, tempFile2.toPath).isEmpty should be(true)
        }
      }

    }

    describe("#processCallbacksFor") {

      it("should get the proper callback for a file path") {
        new MessagingFixtures {
          val cbMap = addCallbackFor(emptyCbMap, List(
            (tempDirPath, ENTRY_CREATE, callbackFunc, false),
            (tempDirLevel2Path, ENTRY_CREATE, callbackFunc, false),
            (tempFileInTempDir, ENTRY_CREATE, callbackFunc, false)
          ))
          monitorActor.processCallbacksFor(cbMap, ENTRY_CREATE, tempFileInTempDir)
          /*
            Fired twice because tempDirPath and tempFileInTempDir are both registered.
            The file path passed to the callback is still the same though because it
            is still the file
          */
          expectMsgAllOf(tempFileInTempDir, tempFileInTempDir)
        }
      }

      it("should get the proper callback for a directory") {
        new MessagingFixtures {
          val cbMap = addCallbackFor(emptyCbMap, List(
            (tempDirPath, ENTRY_MODIFY, callbackFunc, false),
            (tempDirLevel2Path, ENTRY_MODIFY, callbackFunc, false),
            (tempFileInTempDir, ENTRY_MODIFY, callbackFunc, false)
          ))
          monitorActor.processCallbacksFor(cbMap, ENTRY_MODIFY, tempDirPath)
          expectMsg(tempDirPath)
        }
      }

    }

    describe("messaging tests") {

      describe("RegisterCallback message type") {

        it("should register a callback when given a file path") {
          new MessagingFixtures {
            val registerFileCallbackMessage = RegisterCallback(ENTRY_CREATE, None, recursive = false, tempFileInTempDir, callbackFunc)
            monitorActorRef ! registerFileCallbackMessage
            monitorActorRef ! EventAtPath(ENTRY_CREATE, tempFileInTempDir)
            expectMsg(tempFileInTempDir)
          }

        }

        it("should register a callback when given a directory path") {
          new MessagingFixtures {
            val registerFileCallbackMessage = RegisterCallback(ENTRY_MODIFY, None, recursive = false, tempDirPath, callbackFunc)
            monitorActorRef ! registerFileCallbackMessage
            monitorActorRef ! EventAtPath(ENTRY_MODIFY, tempDirPath)
            expectMsg(tempDirPath)
          }
        }

        it("should register a callback recursively for a directory path") {
          new MessagingFixtures {
            val registerFileCallbackMessage = RegisterCallback(ENTRY_DELETE, None, recursive = true, tempDirPath, callbackFunc)
            monitorActorRef ! registerFileCallbackMessage
            monitorActorRef ! EventAtPath(ENTRY_DELETE, tempDirLevel1Path)
            monitorActorRef ! EventAtPath(ENTRY_DELETE, tempDirLevel2Path)
            /*
              Fired 3 times for tempDirLevel1Path because tempDirPath and tempDirLevel1Path are both registered and
              tempDirLevel1Path is a parent of tempDirLevel2Path
              Fired 2 times for tempDirLevel2Path because tempDireLevel1Path and tempDirLevel2Path are both registered
             */
            expectMsgAllOf(tempDirLevel1Path, tempDirLevel1Path, tempDirLevel1Path, tempDirLevel2Path, tempDirLevel2Path)
          }
        }

      }

      describe("UnRegisterCallback message type") {

        it("should un-register a callback when given a file path") {
          new MessagingFixtures {
            monitorActorRef ! RegisterCallback(ENTRY_CREATE, None, recursive = false, tempFileInTempDir, callbackFunc)
            monitorActorRef ! UnRegisterCallback(ENTRY_CREATE, recursive = false, tempFileInTempDir)
            monitorActorRef ! EventAtPath(ENTRY_CREATE, tempFileInTempDir)
            expectNoMsg()
          }

        }

        it("should un-register a callback when given a directory path") {
          new MessagingFixtures {
            monitorActorRef ! RegisterCallback(ENTRY_DELETE, None, recursive = false, tempDirPath, callbackFunc)
            monitorActorRef ! UnRegisterCallback(ENTRY_DELETE, recursive = false, tempDirPath)
            monitorActorRef ! EventAtPath(ENTRY_DELETE, tempDirPath)
            expectNoMsg()
          }
        }

        it("should un-register a callback recursively for a directory path") {
          new MessagingFixtures {
            monitorActorRef ! RegisterCallback(ENTRY_MODIFY, None, recursive = true, tempDirPath, callbackFunc)
            monitorActorRef ! UnRegisterCallback(ENTRY_MODIFY, recursive = true, tempDirPath)
            monitorActorRef ! EventAtPath(ENTRY_MODIFY, tempDirLevel1Path)
            monitorActorRef ! EventAtPath(ENTRY_MODIFY, tempDirLevel1Path)
            expectNoMsg()
          }
        }

        it("should not un-register a callback for a file inside a directory tree even when called recursively") {
          new MessagingFixtures {
            monitorActorRef ! RegisterCallback(ENTRY_CREATE, None, recursive = true, tempDirPath, callbackFunc)
            monitorActorRef ! RegisterCallback(ENTRY_CREATE, None, recursive = true, tempFileInTempDir, callbackFunc)
            monitorActorRef ! UnRegisterCallback(ENTRY_CREATE, recursive = true, tempDirPath)
            monitorActorRef ! EventAtPath(ENTRY_CREATE, tempFileInTempDir)
            expectMsg(tempFileInTempDir)
          }
        }

      }

    }

  }

  describe("integration testing") {

    it("should fire the appropriate callback if a monitored file has been modified") {
      new Fixtures {
        val register = RegisterCallback(ENTRY_MODIFY, None, recursive = false, tempFile.toPath,
          path => testActor ! s"Modified file is at $path")
        monitorActorRef ! register
        // Sleep to make sure that the Java WatchService is monitoring the file ...
        Thread.sleep(1000)
        val writer = new BufferedWriter(new FileWriter(tempFile))
        writer.write("There's text in here wee!!")
        writer.close
        // Within 60 seconds is used in case the Java WatchService is acting slow ...
        within(60 seconds) {
          expectMsg(s"Modified file is at ${tempFile.toPath}")
        }
      }
    }

    it("should fire the appropriate callback if a monitored directory has a directory created inside of it") {
      new Fixtures {
        val register = RegisterCallback(ENTRY_CREATE, None, recursive = false, tempDirPath,
          path => testActor ! s"New thing at $path")
        monitorActorRef ! register
        // Sleep to make sure that the Java WatchService is monitoring the file ...
        Thread.sleep(1000)
        val newDir = new File(s"${tempDirPath.toAbsolutePath}/a-new-dir")
        newDir.mkdir()
        // Within 60 seconds is used in case the Java WatchService is acting slow ...
        within(60 seconds) {
          expectMsg(s"New thing at ${newDir.toPath}")
        }
      }
    }

  }

  describe("Register with specified modifier") {
    it("should use specified modifier for polling event") {
      new Fixtures {
        val monitorActorRef2 = TestActorRef(new MonitorActor(concurrency = 1)) // should make it less temperamental
        var start: Long = _
        var timeLOW: Long = _
        var timeHIGH: Long = _
        // Execute only when `HIGH` is available
        HIGH match {
          case Some(_) =>
            val registerLOW = RegisterCallback(ENTRY_MODIFY, LOW, recursive = false, tempFile.toPath,
              path => {
                timeLOW = System.nanoTime - start
              })
            val registerHIGH = RegisterCallback(ENTRY_MODIFY, HIGH, recursive = false, tempFile.toPath,
              path => {
                timeHIGH = System.nanoTime - start
              })

            start = System.nanoTime
            monitorActorRef2 ! registerLOW
            monitorActorRef2 ! registerHIGH
            // Sleep to make sure that the Java WatchService is monitoring the file ...
            Thread.sleep(3000)
            val writer = new BufferedWriter(new FileWriter(tempFile))
            writer.write("There's text in here wee!!")
            writer.close
            // Wait 10 seconds for finish callback
            Thread.sleep(10000L)
            // SensitivityWatchEventModifier.HIGH should sensitive than SensitivityWatchEventModifier.LOW
            timeHIGH should be <= timeLOW
          case None => true should be(true)
        }
      }
    }

  }

}
