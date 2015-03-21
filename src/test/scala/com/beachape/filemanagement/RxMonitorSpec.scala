package com.beachape.filemanagement

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{PrivateMethodTester, Matchers, FunSpecLike}
import java.nio.file.{Files, Path}
import rx.lang.scala.Observer
import com.beachape.filemanagement.Messages.EventAtPath
import scala.concurrent.Promise
import org.scalatest.concurrent.ScalaFutures
import java.nio.file.StandardWatchEventKinds._

class RxMonitorSpec extends TestKit(ActorSystem("testSystem")) with FunSpecLike with Matchers with PrivateMethodTester with ScalaFutures {

  trait Context {
    val tempDirPath = Files.createTempDirectory("root")
    val pushNextPathToSubject = PrivateMethod[Function[Path, Unit]]('pushNextPathToSubject)
    val monitor = RxMonitor()
    val observable = monitor.observable
    val nextP = Promise[EventAtPath]
    val nextF = nextP.future
    val doneP = Promise[Boolean]
    val doneF = doneP.future
    val observer = Observer(
      onNext = { (p: EventAtPath) => nextP.success(p) },
      onError = { e => doneP.failure(e) },
      onCompleted = () =>  doneP.success(true)
    )
  }

  describe("#stop") {
    it("should call onComplete on the observable's observers") { new Context {
      val subscription = observable.subscribe(observer)
      monitor.stop()
      whenReady(doneF) {_ should be(true) }
    }}
  }

  /*
   Testing a private method here because it is the onl other tricky thing in this
   new extension
  */
  describe("private method pushNextPathToSubject") {
    it("should return a function that will push EventAtPaths to the RxMonitor's observable") { new Context {
      val func = monitor invokePrivate pushNextPathToSubject(ENTRY_MODIFY)
      observable.subscribe(observer)
      func(tempDirPath)
      whenReady(nextF){ _ should be(EventAtPath(ENTRY_MODIFY, tempDirPath)) }
    }}
  }

}
