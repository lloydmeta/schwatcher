package com.beachape.filemanagement

import java.nio.file.{WatchEvent, Path}
import com.beachape.filemanagement.RegistryTypes._

/**
 * Factory object for creating a CallbackRegistry
 * based on a passed in FileEvent
 */
object CallbackRegistry {
  def apply(eventType: WatchEvent.Kind[Path], pathToCallbacksMap: PathToCallbacks = Map()) =
    new CallbackRegistry(eventType, pathToCallbacksMap)
}

/**
 * Immutable class
 * @param eventType
 * @param pathToCallbacksMap
 */
class CallbackRegistry(val eventType: WatchEvent.Kind[Path], pathToCallbacksMap: PathToCallbacks) {

  /**
   * Returns a new instance of CallbackRegistry with the callback registered for the
   * given path
   *
   * @param path Path (Java type) to be registered
   * @param callback Callback function that takes a Path as a parameter and has Unit return type
   * @return a new CallbackRegistry
   */
  def withPathCallback(path: Path, callback: Callback): CallbackRegistry = {
    val totalCallbacksForPath = callback :: pathToCallbacksMap.getOrElse(path, Nil)
    CallbackRegistry(eventType, pathToCallbacksMap ++ Map(path -> totalCallbacksForPath))
  }

  def withoutCallbacksForPath(path: Path) = CallbackRegistry(eventType, pathToCallbacksMap - path)

  /**
   * Returns Some[List[Callback]] registered for the path passed in
   *
   * @param path Path (Java type) to use for checking for callbacks
   * @return Callbacks, which is essentially List[Callback]
   */
  def callbacksForPath(path: Path): Option[Callbacks] = pathToCallbacksMap.get(path)
}