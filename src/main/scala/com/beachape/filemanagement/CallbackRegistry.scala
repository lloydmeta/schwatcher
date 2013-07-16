package com.beachape.filemanagement

import java.nio.file.{WatchEvent, Path}

/**
 * Factory object for creating a CallbackRegistry
 * based on a passed in FileEvent
 */
object CallbackRegistry {
  def apply(eventType: WatchEvent.Kind[Path]) = new CallbackRegistry(eventType)
}

class CallbackRegistry(val eventType: WatchEvent.Kind[Path]) 