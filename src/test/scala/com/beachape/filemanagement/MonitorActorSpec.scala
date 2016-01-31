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

class MonitorActorSpec
    extends FunSpec
    with Matchers
    with BeforeAndAfter
    with PrivateMethodTester {

  private val waitTime = 15 seconds

  abstract class Fixtures extends TestKit(ActorSystem("testSystem")) with ImplicitSender {
    val emptyCbMap = MonitorActor.initialEventTypeCallbackRegistryMap

    // Actor
    val monitorActorRef = TestActorRef(new MonitorActor(concurrency = 1))
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

    val me: Fixtures = this

    // Test helper methods
    def addCallbackFor(
      originalMap: CallbackRegistryMap,
      path: Path,
      event: WatchEvent.Kind[Path],
      callback: Callback,
      recursive: Boolean = false): CallbackRegistryMap = {
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
      recursive: Boolean = false): CallbackRegistryMap = {
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
      pathsCallbackRecursive: List[(Path, WatchEvent.Kind[Path], Callback, Boolean)]): CallbackRegistryMap = {
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
      new Fixtures {
        val thrown = intercept[IllegalArgumentException] {
          TestActorRef(MonitorActor(0))
        }
        thrown.getMessage should be("requirement failed: Callback concurrency requested is 0 but it should at least be 1")
      }
    }

    it("should not throw an error when concurrency parameter is set to 1") {
      new Fixtures {
        TestActorRef(MonitorActor(1))
      }
    }

    it("should not throw an error when concurrency parameter is set to > 1") {
      new Fixtures {
        TestActorRef(MonitorActor(2))
      }
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
          monitorActor.callbacksFor(addedMap, ENTRY_CREATE, tempDirLevel1Path).foreach(callbacks =>
            callbacks should contain(dummyFunction))
          monitorActor.callbacksFor(addedMap, ENTRY_CREATE, tempDirLevel2Path).foreach(callbacks =>
            callbacks should contain(dummyFunction))
        }
      }

      it("should add callbacks for a file path") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempFileInTempDir, ENTRY_CREATE, dummyFunction, true)
          monitorActor.callbacksFor(addedMap, ENTRY_CREATE, tempFileInTempDir).foreach(callbacks =>
            callbacks should contain(dummyFunction))
        }
      }

      it("should not add callbacks recursively if given a file path") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempFileInTempDir, ENTRY_CREATE, dummyFunction, true)
          monitorActor.callbacksFor(addedMap, ENTRY_CREATE, tempDirLevel1Path).foreach(callbacks =>
            callbacks should not contain dummyFunction)
          monitorActor.callbacksFor(addedMap, ENTRY_CREATE, tempDirLevel2Path).foreach(callbacks =>
            callbacks should not contain dummyFunction)
        }
      }

    }

    describe("recursively removing callbacks") {

      it("should remove callbacks for all folders that exist under the path given") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempDirPath, ENTRY_CREATE, dummyFunction, true)
          val removedMap = removeCallbacksFor(addedMap, tempDirPath, ENTRY_CREATE, true)
          monitorActor.callbacksFor(removedMap, ENTRY_CREATE, tempDirLevel1Path).isEmpty shouldBe true
          monitorActor.callbacksFor(removedMap, ENTRY_CREATE, tempDirLevel2Path).isEmpty shouldBe true
        }
      }

      it("should remove callbacks for a file path") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempFileInTempDir, ENTRY_CREATE, dummyFunction, true)
          val removedMap = removeCallbacksFor(addedMap, tempFileInTempDir, ENTRY_CREATE, true)
          monitorActor.callbacksFor(removedMap, ENTRY_CREATE, tempFileInTempDir).isEmpty shouldBe true
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
          monitorActor.callbacksFor(emptyCbMap, ENTRY_CREATE, tempFile2.toPath).isEmpty shouldBe true
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
          monitorActor.processCallbacksFor(cbMap, emptyCbMap, ENTRY_CREATE, tempFileInTempDir)
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
          monitorActor.processCallbacksFor(cbMap, emptyCbMap, ENTRY_MODIFY, tempDirPath)
          expectMsg(tempDirPath)
        }
      }

    }

    describe("messaging tests") {

      describe("RegisterCallback message type") {

        it("should register a callback when given a file path") {
          new MessagingFixtures {
            val registerFileCallbackMessage = RegisterCallback(
              event = ENTRY_CREATE,
              modifier = None,
              recursive = false,
              persistent = false,
              path = tempFileInTempDir,
              callback = callbackFunc
            )
            monitorActorRef ! registerFileCallbackMessage
            monitorActorRef ! EventAtPath(ENTRY_CREATE, tempFileInTempDir)
            expectMsg(tempFileInTempDir)
          }

        }

        it("should register a callback when given a directory path") {
          new MessagingFixtures {
            val registerFileCallbackMessage = RegisterCallback(
              event = ENTRY_MODIFY,
              modifier = None,
              recursive = false,
              persistent = false,
              path = tempDirPath,
              callback = callbackFunc
            )
            monitorActorRef ! registerFileCallbackMessage
            monitorActorRef ! EventAtPath(ENTRY_MODIFY, tempDirPath)
            expectMsg(tempDirPath)
          }
        }

        it("should register a callback recursively for a directory path") {
          new MessagingFixtures {
            val registerFileCallbackMessage = RegisterCallback(
              event = ENTRY_DELETE,
              modifier = None,
              recursive = true,
              persistent = false,
              path = tempDirPath,
              callback = callbackFunc
            )
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
            monitorActorRef ! RegisterCallback(
              event = ENTRY_CREATE,
              modifier = None,
              recursive = false,
              persistent = false,
              path = tempFileInTempDir,
              callback = callbackFunc
            )
            monitorActorRef ! UnRegisterCallback(ENTRY_CREATE, tempFileInTempDir)
            monitorActorRef ! EventAtPath(ENTRY_CREATE, tempFileInTempDir)
            expectNoMsg()
          }

        }

        it("should un-register a callback when given a directory path") {
          new MessagingFixtures {
            monitorActorRef ! RegisterCallback(
              event = ENTRY_DELETE,
              modifier = None,
              recursive = false,
              path = tempDirPath,
              callback = callbackFunc
            )
            monitorActorRef ! UnRegisterCallback(ENTRY_DELETE, tempDirPath)
            monitorActorRef ! EventAtPath(ENTRY_DELETE, tempDirPath)
            expectNoMsg()
          }
        }

        it("should un-register a callback recursively for a directory path") {
          new MessagingFixtures {
            monitorActorRef ! RegisterCallback(
              event = ENTRY_MODIFY,
              modifier = None,
              recursive = true,
              persistent = true,
              path = tempDirPath,
              callback = callbackFunc
            )
            monitorActorRef ! UnRegisterCallback(ENTRY_MODIFY, tempDirPath, recursive = true)
            monitorActorRef ! EventAtPath(ENTRY_MODIFY, tempDirLevel1Path)
            monitorActorRef ! EventAtPath(ENTRY_MODIFY, tempDirLevel1Path)
            expectNoMsg()
          }
        }

        it("should not un-register a callback for a file inside a directory tree even when called recursively") {
          new MessagingFixtures {
            monitorActorRef ! RegisterCallback(
              event = ENTRY_CREATE,
              modifier = None,
              recursive = true,
              persistent = true,
              path = tempDirPath,
              callback = callbackFunc
            )
            monitorActorRef ! RegisterCallback(
              event = ENTRY_CREATE,
              modifier = None,
              recursive = true,
              path = tempFileInTempDir,
              callback = callbackFunc
            )
            monitorActorRef ! UnRegisterCallback(ENTRY_CREATE, tempDirPath, recursive = true)
            monitorActorRef ! EventAtPath(ENTRY_CREATE, tempFileInTempDir)
            expectMsg(tempFileInTempDir)
          }
        }

      }

    }

  }

  describe("integration testing") {

    describe("file created in a nested directory") {

      def testRegistration[A](register: Fixtures => RegisterCallbackMessage, expectedMsg: Path => A): Unit = {
        new Fixtures {
          monitorActorRef ! register(me)
          // Sleep to make sure that the Java WatchService is monitoring the file ...
          Thread.sleep(1000)
          val newDir = new File(s"${tempDirPath.toAbsolutePath}/a-new-dir")
          newDir.mkdir()
          // Within 60 seconds is used in case the Java WatchService is acting slow ...
          within(waitTime) {
            expectMsg(expectedMsg(newDir.toPath))
          }
        }
      }

      it("should fire the appropriate callback") {
        testRegistration(
          fixture => RegisterCallback(
            event = ENTRY_CREATE,
            path = fixture.tempDirPath,
            callback = path => fixture.testActor ! s"New thing at $path"
          ),
          path => s"New thing at $path"
        )
      }

      it("should forward the message to the appropriate actor") {
        testRegistration(
          fixture => RegisterSubscriber(
            event = ENTRY_CREATE,
            path = fixture.tempDirPath,
            subscriber = fixture.testActor
          ),
          path => EventAtPath(ENTRY_CREATE, path)
        )
      }

    }

    describe("file modified") {

      def testRegistration[A](register: Fixtures => RegisterCallbackMessage, expectedMsg: Path => A): Unit = {
        new Fixtures {
          monitorActorRef ! register(me)
          // Sleep to make sure that the Java WatchService is monitoring the file ...
          Thread.sleep(1000)
          val writer = new BufferedWriter(new FileWriter(tempFile))
          writer.write("There's text in here wee!!")
          writer.close()
          // Within 60 seconds is used in case the Java WatchService is acting slow ...
          within(waitTime) {
            expectMsg(expectedMsg(tempFile.toPath))
          }
        }
      }

      it("should fire the appropriate callback") {
        testRegistration(
          fixture => RegisterCallback(
            event = ENTRY_MODIFY,
            path = fixture.tempFile.toPath,
            callback = path => fixture.testActor ! s"Modified file is at $path"
          ),
          path => s"Modified file is at $path"
        )
      }

      it("should forward the message to the appropriate actor") {
        testRegistration(
          fixture => RegisterSubscriber(
            event = ENTRY_MODIFY,
            path = fixture.tempFile.toPath,
            subscriber = fixture.testActor
          ),
          path => EventAtPath(ENTRY_MODIFY, path)
        )
      }

    }

  }

  describe("persistent registration") {

    describe("for modify events on newly created files") {

      def testRegistration[A](register: Fixtures => RegisterCallbackMessage, expectedMsg: Path => A): Unit = {
        new Fixtures {
          monitorActorRef ! register(me)
          // Sleep to make sure that the Java WatchService is monitoring the file ...
          Thread.sleep(1000)
          val newlyCreatedFile = Files.createTempFile(tempDirPath, "hopefully-works", ".txt")
          newlyCreatedFile.toFile.deleteOnExit()
          // Sleep to make sure that the Java WatchService is monitoring the new file ...
          Thread.sleep(10000)
          val writer = new BufferedWriter(new FileWriter(newlyCreatedFile.toFile))
          writer.write("There's text in here wee!!")
          writer.close()
          // Within 60 seconds is used in case the Java WatchService is acting slow ...
          within(waitTime) {
            expectMsg(expectedMsg(newlyCreatedFile))
          }
        }
      }

      it("should fire proper callbacks for modify events") {
        testRegistration(
          fixture => {
            RegisterCallback(
              event = ENTRY_MODIFY,
              path = fixture.tempDirPath,
              callback = path => fixture.testActor ! s"Modified file is at $path",
              persistent = true
            )
          },
          path => s"Modified file is at $path"
        )
      }

      it("should forward to the proper ActorRef for modify events") {
        testRegistration(
          fixture =>
            RegisterSubscriber(
              event = ENTRY_MODIFY,
              path = fixture.tempDirPath,
              subscriber = fixture.testActor,
              persistent = true
            ),
          path => EventAtPath(ENTRY_MODIFY, path)
        )
      }

    }

    describe("deletion on newly created files") {

      def testRegistration[A](register: Fixtures => RegisterCallbackMessage, expectedMsg: Path => A): Unit = {
        new Fixtures {
          monitorActorRef ! register(me)
          // Sleep to make sure that the Java WatchService is monitoring the file ...
          Thread.sleep(1000)
          val newDirectory = Files.createTempDirectory(tempDirPath, "new-dir")
          Thread.sleep(1000)
          val newlyCreatedFile = Files.createTempFile(newDirectory, "new-file", ".tmp")
          newDirectory.toFile.deleteOnExit()
          newlyCreatedFile.toFile.deleteOnExit()
          // Sleep to make sure that the Java WatchService is monitoring the new file ...
          Thread.sleep(10000)
          newlyCreatedFile.toFile.delete()
          // Within 60 seconds is used in case the Java WatchService is acting slow ...
          within(waitTime) {
            expectMsg(expectedMsg(newlyCreatedFile))
          }
        }
      }

      it("should fire the proper callback") {
        testRegistration(
          fixture => RegisterCallback(
            event = ENTRY_DELETE,
            path = fixture.tempDirPath,
            callback = path => fixture.testActor ! s"Deleted file is at $path",
            persistent = true
          ),
          path => s"Deleted file is at $path"
        )
      }

      it("should forward the message to the appropriate actor") {
        testRegistration(
          fixture => RegisterSubscriber(
            event = ENTRY_DELETE,
            path = fixture.tempDirPath,
            subscriber = fixture.testActor,
            persistent = true
          ),
          path => EventAtPath(ENTRY_DELETE, path)
        )
      }

    }

    describe("creation within newly created directories") {

      def testRegistration[A](register: Fixtures => RegisterCallbackMessage, expectedMsg: Path => A): Unit = {
        new Fixtures {
          monitorActorRef ! register(me)
          // Sleep to make sure that the Java WatchService is monitoring the file ...
          Thread.sleep(10000)
          val newDirectory = Files.createTempDirectory(tempDirPath, "new-dir")
          newDirectory.toFile.deleteOnExit()
          within(waitTime) {
            expectMsg(expectedMsg(newDirectory))
          }
          Thread.sleep(10000)
          val newlyCreatedFile = Files.createTempFile(newDirectory, "new-file", ".tmp")
          newlyCreatedFile.toFile.deleteOnExit()
          // Within 60 seconds is used in case the Java WatchService is acting slow ...
          within(waitTime) {
            expectMsg(expectedMsg(newlyCreatedFile))
          }
        }
      }

      it("should fire the proper callback") {
        testRegistration(
          fixture => RegisterCallback(
            event = ENTRY_CREATE,
            path = fixture.tempDirPath,
            callback = path => fixture.testActor ! s"Created file is at $path",
            persistent = true
          ),
          path => s"Created file is at $path"
        )
      }

      it("should forward the message to the appropriate actor") {
        testRegistration(
          fixture => RegisterSubscriber(
            event = ENTRY_CREATE,
            path = fixture.tempDirPath,
            subscriber = fixture.testActor,
            persistent = true
          ),
          path => EventAtPath(ENTRY_CREATE, path)
        )
      }

    }

  }

  describe("Register with specified modifier") {
    it("should use specified modifier for polling event") {
      new Fixtures {
        var start: Long = _
        var timeLOW: Long = _
        var timeHIGH: Long = _
        // Execute only when `HIGH` is available
        HIGH match {
          case Some(_) =>
            val registerLOW = RegisterCallback(
              event = ENTRY_MODIFY,
              path = tempFile.toPath,
              callback = path => {
                timeLOW = System.nanoTime - start
              },
              modifier = LOW
            )
            val registerHIGH = RegisterCallback(
              event = ENTRY_MODIFY,
              path = tempFile.toPath,
              callback = path => {
                timeHIGH = System.nanoTime - start
              },
              modifier = HIGH
            )

            start = System.nanoTime
            monitorActorRef ! registerLOW
            monitorActorRef ! registerHIGH
            // Sleep to make sure that the Java WatchService is monitoring the file ...
            Thread.sleep(3000)
            val writer = new BufferedWriter(new FileWriter(tempFile))
            writer.write("There's text in here wee!!")
            writer.close()
            // Wait 10 seconds for finish callback
            Thread.sleep(10000L)
            // SensitivityWatchEventModifier.HIGH should sensitive than SensitivityWatchEventModifier.LOW
            timeHIGH should be <= timeLOW
          case None =>
        }
      }
    }

  }

}
