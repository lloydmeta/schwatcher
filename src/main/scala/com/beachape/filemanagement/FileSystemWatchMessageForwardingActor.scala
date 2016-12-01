package com.beachape

/**
 * Created by arunavs on 9/27/16.
 *
 * This is a simple actor that can be used directly to send a message
 * whenever a dir is modified.
 *
 * It sends the changed dir message whenever changes happen to the
 * directory.
 */

import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds._

import akka.actor.ActorRef
import com.beachape.filemanagement.Messages.{ EventAtPath, RegisterCallback }
import com.beachape.filemanagement.MonitorActor
import com.beachape.filemanagement.RegistryTypes.Callback

class FileSystemWatchMessageForwardingActor(dirPaths: Seq[Path],
    sendToActor: ActorRef) extends MonitorActor {

  private val fileCreatedCallback: Callback = { path =>
    sendToActor ! EventAtPath(ENTRY_CREATE, path)
  }

  private val fileModifiedCallback: Callback = { path =>
    sendToActor ! EventAtPath(ENTRY_MODIFY, path)
  }

  private val fileDeletedCallback: Callback = { path =>
    sendToActor ! EventAtPath(ENTRY_DELETE, path)
  }

  // Register callback for each path create
  dirPaths.foreach(dirPath => self ! RegisterCallback(
    event = ENTRY_CREATE,
    path = dirPath,
    callback = fileCreatedCallback
  ))

  // Register callback for each path modify
  dirPaths.foreach(dirPath => self ! RegisterCallback(
    event = ENTRY_MODIFY,
    path = dirPath,
    callback = fileModifiedCallback
  ))

  // Register callback for each path create
  dirPaths.foreach(dirPath => self ! RegisterCallback(
    event = ENTRY_DELETE,
    path = dirPath,
    callback = fileDeletedCallback
  ))
}