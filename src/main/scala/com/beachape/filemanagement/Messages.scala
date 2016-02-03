package com.beachape.filemanagement

import akka.actor.ActorRef
import com.beachape.filemanagement.RegistryTypes._
import java.nio.file.{ Path, WatchEvent }
import java.nio.file.WatchEvent.Modifier
import scala.language.existentials

/*
 * Message case classes to make passing messages
 *  between actors safer and easier
 */

object Messages {

  /**
   * Base trait for registering callbacks
   */
  sealed trait RegisterCallbackMessage {
    def event: WatchEvent.Kind[Path]
    def modifier: Option[Modifier]
    def recursive: Boolean
    def path: Path
    def callback: Callback
    def bossy: Boolean = false // "Bossy" means whether or not a message is supposed to remove all other callbacks
    def persistent: Boolean
  }

  /**
   * Trait to make a message definition "Bossy", meaning it will remove all other callbacks for a specific path and
   * event type
   */
  sealed trait BossyMessage { this: RegisterCallbackMessage =>
    final override def bossy: Boolean = true
  }

  /**
   * Abstract class to make it easy to define a registration that forwards EvenAtPath messages to a particular [[ActorRef]]
   */
  sealed abstract class ForwardToSubscriber(subscriber: ActorRef) { this: RegisterCallbackMessage =>
    val callback: Callback = { path =>
      subscriber ! EventAtPath(event, path)
    }
  }

  /**
   * Message case class for telling a MonitorActor that an
   * event has happened and at what path
   *
   * @param event WatchEvent.Kind[Path], one of ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE
   * @param path Path (Java object) pointing to a file/directory
   */
  sealed case class EventAtPath(event: WatchEvent.Kind[_], path: Path)

  /**
   * Message case class for telling a MonitorActor to register a
   * path for callback
   *
   * @param event WatchEvent.Kind[Path], one of ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE
   * @param path Path (Java object) pointing to a file/directory
   * @param callback (Path) => Unit type function
   * @param recursive Boolean To recursively register the callback or not, defaults to false
   * @param persistent Boolean To automatically add the same callback to new files or not, defaults to false
   */
  sealed case class RegisterCallback(
    event: WatchEvent.Kind[Path],
    path: Path,
    callback: Callback,
    modifier: Option[Modifier] = None,
    recursive: Boolean = false,
    persistent: Boolean = false) extends RegisterCallbackMessage

  /**
   * Message case class for registering an ActorRef to receive [[EventAtPath]] messages when something happens
   * at a path
   *
   * @param event WatchEvent.Kind[Path], one of ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE
   * @param path Path (Java object) pointing to a file/directory
   * @param subscriber ActorRef that you want to receive notifications on
   * @param modifier Optional Modifier that qualifies how a Watchable is registered with a WatchService.
   * @param recursive Boolean To recursively register the callback or not, defaults to false
   * @param persistent Boolean To automatically add the same callback to new files or not, defaults to false
   */
  sealed case class RegisterSubscriber(event: WatchEvent.Kind[Path],
    path: Path,
    subscriber: ActorRef,
    modifier: Option[Modifier] = None,
    recursive: Boolean = false,
    persistent: Boolean = false) extends ForwardToSubscriber(subscriber) with RegisterCallbackMessage

  /**
   * Message case class for telling a MonitorActor that the callback contained will be the ONLY callback registered for
   * the specific path.
   *
   * Mostly intended to be used with RxMonitor; hence the terrible name.
   *
   * @param event WatchEvent.Kind[Path], one of ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE
   * @param path Path (Java object) pointing to a file/directory
   * @param callback (Path) => Unit type function
   * @param modifier Optional Modifier that qualifies how a Watchable is registered with a WatchService.
   * @param recursive Boolean to specify to recursively register the callback or not, defaults to false
   * @param persistent Boolean To automatically add the same callback to new files or not, defaults to false
   */
  sealed case class RegisterBossyCallback(
    event: WatchEvent.Kind[Path],
    path: Path,
    callback: Callback,
    modifier: Option[Modifier] = None,
    recursive: Boolean = false,
    persistent: Boolean = false) extends RegisterCallbackMessage with BossyMessage

  /**
   * Message case class for registering an ActorRef to receive [[EventAtPath]] messages when something happens
   * at a path.
   *
   * This is different from RegisterSubscriber in that when something happens at the provided path, the only thing
   * that will happen is the ActorRef you provided will get notified.
   *
   * @param event WatchEvent.Kind[Path], one of ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE
   * @param path Path (Java object) pointing to a file/directory
   * @param subscriber ActorRef that you want to receive notifications on
   * @param modifier Optional Modifier that qualifies how a Watchable is registered with a WatchService.
   * @param recursive Boolean To recursively register the callback or not, defaults to false
   * @param persistent Boolean To automatically add the same callback to new files or not, defaults to false
   */
  sealed case class RegisterBossySubscriber(
    event: WatchEvent.Kind[Path],
    path: Path,
    subscriber: ActorRef,
    modifier: Option[Modifier] = None,
    recursive: Boolean = false,
    persistent: Boolean = false) extends ForwardToSubscriber(subscriber) with RegisterCallbackMessage with BossyMessage

  /**
   * Message case class for telling a MonitorActor to un-register a
   * path for callback
   *
   * Note that this does not remove the event listeners from the Java API,
   * because such functionality does not exist. All this does is make sure that the callbacks
   * registered to a specific path do not get fired. Depending on your use case,
   * it may make more sense to just kill the monitor actor to start fresh.
   *
   * @param event WatchEvent.Kind[Path], one of ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE
   * @param recursive Boolean to specify to recursively register the callback or not, defaults to false
   * @param path Path (Java object) pointing to a file/directory
   */
  sealed case class UnRegisterCallback(
    event: WatchEvent.Kind[Path],
    path: Path,
    recursive: Boolean = false)

  /**
   * Message case class for telling a CallbackActor to perform a callback
   *
   * @param path Path (Java object) pointing to a file/directory
   * @param callback (Path) => Unit type function
   */
  sealed case class PerformCallback(path: Path, callback: Callback)
}