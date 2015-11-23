package com.beachape.filemanagement

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
    def isBossy: Boolean
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
   * @param recursive Boolean to specify to recursively register the callback or not, defaults to false
   * @param path Path (Java object) pointing to a file/directory
   * @param callback (Path) => Unit type function
   */
  sealed case class RegisterCallback(
      event: WatchEvent.Kind[Path],
      modifier: Option[Modifier] = None,
      recursive: Boolean = false,
      path: Path,
      callback: Callback
  ) extends RegisterCallbackMessage {
    val isBossy = false
  }

  /**
   * Message case class for telling a MonitorActor that the callback contained
   * will be the ONLY callback registered for the specific path.
   *
   * Mostly intended to be used with RxMonitor; hence the terrible name.
   *
   * @param event WatchEvent.Kind[Path], one of ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE
   * @param recursive Boolean to specify to recursively register the callback or not, defaults to false
   * @param path Path (Java object) pointing to a file/directory
   * @param callback (Path) => Unit type function
   */
  sealed case class RegisterBossyCallback(
      event: WatchEvent.Kind[Path],
      modifier: Option[Modifier] = None,
      recursive: Boolean = false,
      path: Path,
      callback: Callback
  ) extends RegisterCallbackMessage {
    val isBossy = true
  }

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
    recursive: Boolean = false,
    path: Path
  )

  /**
   * Message case class for telling a CallbackActor to perform a callback
   * @param path Path (Java object) pointing to a file/directory
   * @param callback (Path) => Unit type function
   */
  sealed case class PerformCallback(path: Path, callback: Callback)
}