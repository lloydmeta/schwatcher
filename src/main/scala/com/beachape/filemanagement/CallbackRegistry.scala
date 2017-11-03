package com.beachape.filemanagement

import com.beachape.filemanagement.RegistryTypes._
import java.nio.file.Path

/**
  * Factory object for creating a CallbackRegistry
  * based on a passed in FileEvent
  */
object CallbackRegistry {

  /**
    * Factory method that returns a CallbackRegistry
    *
    * @param pathToCallbacksMap Optional Map[Path,List[Callbacks]] for dereferencing Paths and callback
    * @return
    */
  def apply(pathToCallbacksMap: Map[Path, List[Callback]] = Map()) =
    new CallbackRegistry(pathToCallbacksMap)
}

/**
  * Immutable class for holding the callbacks for a given path
  *
  * Should be instantiated via companion object above
  * @param pathToCallbacksMap Map[Path,List[Callbacks]] for dereferencing Paths and callback
  */
class CallbackRegistry(pathToCallbacksMap: Map[Path, List[Callback]]) extends RecursiveFileActions {

  /**
    * Returns a new instance of CallbackRegistry with the callback registered for the
    * given path. If specified, the callback will be registered recursively for
    * each subdirectory of the given path.
    *
    * Some call this a monadic method
    *
    * @param path Path (Java type) to be registered for callbacks
    * @param callback Callback function that takes a Path as a parameter and has Unit return type
    * @param recursive Boolean register the callback for each subdirectory
    * @param bossy Boolean register this callback as the only one for this path
    * @return a new CallbackRegistry
    */
  def withCallbackFor(path: Path,
                      callback: Callback,
                      recursive: Boolean = false,
                      bossy: Boolean = false): CallbackRegistry = {
    val callbacks = if (bossy) {
      List(callback)
    } else {
      callback :: pathToCallbacksMap.getOrElse(path, Nil)
    }
    var callbackRegistry = CallbackRegistry(pathToCallbacksMap + (path -> callbacks))
    if (recursive) forEachDir(path) { subDir =>
      callbackRegistry = callbackRegistry.withCallbackFor(subDir, callback)
    }
    callbackRegistry
  }

  /**
    * Returns a new instance of CallbackRegistry without callbacks for the given
    * path. If specified, callbacks will be unregistered recursively for each
    * subdirectory of the given path.
    *
    * Some call this a monadic method
    *
    * @param path Path (Java type) to be unregistered
    * @param recursive Boolean unregister callbacks for each subdirectory
    * @return a new CallbackRegistry
    */
  def withoutCallbacksFor(path: Path, recursive: Boolean = false): CallbackRegistry = {
    var callbackRegistry = CallbackRegistry(pathToCallbacksMap - path)
    if (recursive) forEachDir(path) { subDir =>
      callbackRegistry = callbackRegistry.withoutCallbacksFor(subDir)
    }
    callbackRegistry
  }

  /**
    * Returns Some[List[Callback]] registered for the path passed in
    *
    * @param path Path (Java type) to use for checking for callbacks
    * @return Some[Callbacks], which is essentially List[Callback]
    */
  def callbacksFor(path: Path): Option[Callbacks] = pathToCallbacksMap.get(path)
}
