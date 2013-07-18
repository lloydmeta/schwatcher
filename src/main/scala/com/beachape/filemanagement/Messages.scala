package com.beachape.filemanagement

import com.beachape.filemanagement.RegistryTypes._
import java.nio.file.{Path, WatchEvent}
import scala.language.existentials

/*
 * Message case classes to make passing messages
 *  between actors safer and easier
 */

object Messages{
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
                                      recursive: Boolean = false,
                                      path: Path,
                                      callback: Callback)

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
                                        path: Path)

  /**
   * Message case class for telling a CallbackActor to perform a callback
   * @param path Path (Java object) pointing to a file/directory
   * @param callback (Path) => Unit type function
   */
  sealed case class PerformCallback(path: Path, callback: Callback)
}