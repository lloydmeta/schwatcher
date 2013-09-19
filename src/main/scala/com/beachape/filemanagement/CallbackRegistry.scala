package com.beachape.filemanagement

import com.beachape.filemanagement.RegistryTypes._
import java.nio.file.{WatchEvent, Path}

/**
 * Factory object for creating a CallbackRegistry
 * based on a passed in FileEvent
 */
object CallbackRegistry {
  /**
   * Factory method that returns a CallbackRegistry
   *
   * @param eventType WatchEvent.Kind[Path] Java7 Event type
   * @param pathToCallbacksMap Optional Map[Path,List[Callbacks]] for dereferencing Paths and callback
   * @return
   */
  def apply(eventType: WatchEvent.Kind[Path], pathToCallbacksMap: PathToCallbacks = Map()) =
    new CallbackRegistry(eventType, pathToCallbacksMap)
}

/**
 * Immutable class for holding the callbacks for a given path
 *
 * Should be instantiated via companion object above
 * @param eventType WatchEvent.Kind[Path] Java7 Event type
 * @param pathToCallbacksMap Map[Path,List[Callbacks]] for dereferencing Paths and callback
 */
class CallbackRegistry(val eventType: WatchEvent.Kind[Path], pathToCallbacksMap: PathToCallbacks)
  extends RecursiveFileActions {
  /**
   * Returns a new instance of CallbackRegistry with the callback registered for the
   * given path
   *
   * Some call this a monadic method
   *
   * @param path Path (Java type) to be registered
   * @param callback Callback function that takes a Path as a parameter and has Unit return type
   * @return a new CallbackRegistry
   */
  def withPathCallback(path: Path, callback: Callback): CallbackRegistry = {
    val totalCallbacksForPath = callback :: pathToCallbacksMap.getOrElse(path, Nil)
    CallbackRegistry(eventType, pathToCallbacksMap + (path -> totalCallbacksForPath))
  }

  /**
   * Returns a new instance of CallbackRegistry with the callback registered for the
   * given path, but does it recursively
   *
   * Some call this a monadic method
   *
   * @param path Path (Java type) to be registered for callbacks recursively
   * @param callback Callback function that takes a Path as a parameter and has Unit return type
   * @return a new CallbackRegistry
   */
  def withPathCallbackRecursive(path: Path, callback: Callback): CallbackRegistry = {
    var callbackRegistry = withPathCallback(path, callback)
    forEachDir(path) { (containedDirPath, _) =>
      callbackRegistry = callbackRegistry.withPathCallback(containedDirPath, callback)
    }
    callbackRegistry
  }

  /**
   * Returns a new instance of CallbackRegistry without callbacks for the specified path
   *
   * Some call this a monadic method
   *
   * @param path Path (Java type) to be unregistered
   * @return a new CallbackRegistry
   */
  def withoutCallbacksForPath(path: Path) = CallbackRegistry(eventType, pathToCallbacksMap - path)

  /**
   * Returns a new instance of CallbackRegistry without callbacks for the specified path, but
   * recursively so that all folders under this folder are also unregistered for callbacks
   *
   * Some call this a monadic method
   *
   * @param path Path (Java type) to be unregistered recursively
   * @return a new CallbackRegistry
   */
  def withoutCallbacksForPathRecursive(path: Path): CallbackRegistry = {
    var callbackRegistry = withoutCallbacksForPath(path)
    forEachDir(path) { (containedDirPath, _) =>
      callbackRegistry = callbackRegistry.withoutCallbacksForPath(containedDirPath)
    }
    callbackRegistry
  }

  /**
   * Returns Some[List[Callback]] registered for the path passed in
   *
   * @param path Path (Java type) to use for checking for callbacks
   * @return Some[Callbacks], which is essentially List[Callback]
   */
  def callbacksForPath(path: Path): Option[Callbacks] = pathToCallbacksMap.get(path)
}
