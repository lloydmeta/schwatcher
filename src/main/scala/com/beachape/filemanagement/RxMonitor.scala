package com.beachape.filemanagement

import rx.subjects.PublishSubject
import java.nio.file.{WatchEvent, Path}
import akka.actor.{PoisonPill, ActorSystem}
import com.beachape.filemanagement.Messages.{UnRegisterCallback, RegisterBossyCallback}
import java.nio.file.WatchEvent.Modifier
import rx.lang.scala.Observable
import rx.lang.scala.JavaConversions.toScalaObservable

object RxMonitor {

  def apply(implicit actorSystem: ActorSystem = ActorSystem("actorSystem")): RxMonitor = new RxMonitor(actorSystem)
}

/**
 * Reactive Extensions-based class
 *
 * If you want to have a Stream of some kind
 *
 * Created by Lloyd on 5/1/14.
 */
class RxMonitor(actorSystem: ActorSystem) {

  private val rxSubject = PublishSubject.create[Path]
  private val monitorActor = actorSystem.actorOf(MonitorActor(concurrency = 1))
  private val pushNextPathToSubject = { p: Path => rxSubject.onNext(p) }

  /**
   * Returns an Observable that will spew out [[Path]]s over time based on
   * what paths are registered and unregistered to this RxMonitor
   */
  def observable(): Observable[Path] = toScalaObservable(rxSubject.asObservable())

  /**
   * Registers a path for monitoring
   *
   * Note that this is an asynchronous operation
   */
  def registerPath(event: WatchEvent.Kind[Path],
                   path: Path,
                   recursive: Boolean = false,
                   modifier: Option[Modifier] = None) {
    monitorActor ! RegisterBossyCallback(
      event = event,
      modifier = modifier,
      recursive = recursive,
      path = path,
      callback = pushNextPathToSubject)
  }

  /**
   * Unregisters a path from monitoring
   *
   * Note that this is an asynchronous operation
   */
  def unregisterPath(event: WatchEvent.Kind[Path],
                     path: Path,
                     recursive: Boolean = false,
                     modifier: Option[Modifier] = None) {
    monitorActor ! UnRegisterCallback(
      event = event,
      recursive = recursive,
      path = path)
  }

  /**
   * Stops any kind of monitoring and signals to the observers of this
   * RxMonitor instance that the Observable is completed.
   */
  def stop() {
    monitorActor ! PoisonPill
    rxSubject.onCompleted()
  }
}
