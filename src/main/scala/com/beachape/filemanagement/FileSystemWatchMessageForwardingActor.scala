package com.beachape.filemanagement

/**
  * Created by arunavs on 9/27/16.
  */
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.WatchEvent.{Kind, Modifier}

import akka.actor.ActorRef
import com.beachape.filemanagement.Messages.{EventAtPath, RegisterCallback}

/**
  * This is a simple actor that can be used directly to send a message
  * whenever a dir is modified.
  *
  * It sends the changed dir message whenever changes happen to the
  * directory.
  *
  * @param dirPaths Paths to register for event messages
  * @param sendToActor to send evet messages to
  * @param eventKinds kinds of path events to monitor on (Defaults to Create, Delete, and Modify)
  * @param persistent whether or not to continually monitor newly created files after initial registration (Default: true)
  * @param recursive whether or not to recursively monitor a directory structure (Default: true)
  */
class FileSystemWatchMessageForwardingActor(dirPaths: Seq[Path],
                                            sendToActor: ActorRef,
                                            eventKinds: Seq[Kind[Path]] =
                                              Seq(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY),
                                            persistent: Boolean = true,
                                            recursive: Boolean = false,
                                            modifier: Option[Modifier] = None)
    extends MonitorActor {

  for {
    kind    <- eventKinds
    dirPath <- dirPaths
  } {
    self ! RegisterCallback(
      event = kind,
      path = dirPath,
      callback = { path =>
        sendToActor ! EventAtPath(kind, path)
      },
      persistent = persistent,
      recursive = recursive,
      modifier = modifier
    )
  }
}
