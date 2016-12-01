package com.beachape.filemanagement

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import com.beachape.filemanagement.Messages.PerformCallback
import com.beachape.filemanagement.RegistryTypes._
import java.nio.file.Paths
import org.scalatest._
import scala.concurrent.{ExecutionContext, Promise}
import ExecutionContext.Implicits.global
import scala.concurrent.Promise
import scala.util.Success

class CallbackActorSpec extends FunSpec with Matchers with BeforeAndAfter {

  abstract class Fixtures extends TestKit(ActorSystem("testSystem")) with ImplicitSender {
    val callbackActor = system.actorOf(CallbackActor())
    val tmpDirPath    = Paths get System.getProperty("java.io.tmpdir")
    val p             = Promise[Int]()
    val dummyFunction: Callback = { path =>
      p.success(1)
    }
    val dummySendFunction: Callback = { path =>
      testActor ! "Up yours!"
    }
  }

  describe("#receive") {

    it("should take a PerformCallback object and perform the callback") {
      new Fixtures {
        callbackActor ! PerformCallback(tmpDirPath, dummyFunction)
        p.future.onComplete {
          case Success(value) => value should be(1)
          case _              => true should be(false)
        }
      }

    }

    it("should work with callbacks that send messages") {
      new Fixtures {
        callbackActor ! PerformCallback(tmpDirPath, dummySendFunction)
        expectMsg("Up yours!")
      }
    }

  }
}
