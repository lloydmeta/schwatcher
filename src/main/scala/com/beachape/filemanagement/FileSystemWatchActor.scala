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

import java.nio.file.{ Path, Paths }

import com.beachape.filemanagement.Messages.RegisterCallback
import com.beachape.filemanagement.MonitorActor
import com.beachape.filemanagement.RegistryTypes.Callback
import java.nio.file.StandardWatchEventKinds._

import akka.actor.ActorRef
import com.beachape.FileSystemWatchActor.ChangedDir

class FileSystemWatchActor(dirs: List[String], sendToActor: ActorRef) extends MonitorActor {

  val changedCallback: Callback = { path =>
    sendChangedDirMsg(path)
  }

  // Create a paths list from a dirs list
  val paths = dirs.map(f => Paths get f).map(_.normalize())

  // Register callback for each path create
  paths.foreach(dir => self ! RegisterCallback(
    event = ENTRY_CREATE,
    path = dir,
    callback = changedCallback
  ))

  // Register callback for each path modify
  paths.foreach(dir => self ! RegisterCallback(
    event = ENTRY_MODIFY,
    path = dir,
    callback = changedCallback
  ))

  // Register callback for each path create
  paths.foreach(dir => self ! RegisterCallback(
    event = ENTRY_DELETE,
    path = dir,
    callback = changedCallback
  ))

  private def sendChangedDirMsg(path: Path): Unit = {
    sendToActor ! ChangedDir(path)
  }
}

object FileSystemWatchActor {

  // Message generated whenever modifications happen to a directory.
  case class ChangedDir(val paths: Path)
}
