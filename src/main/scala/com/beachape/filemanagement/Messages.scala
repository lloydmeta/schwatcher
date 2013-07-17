package com.beachape.filemanagement

import java.nio.file.{Path, WatchEvent}

/*
 * Message case classes to make passing messages
 *  between actors safer and easier
 */

/**
 * Message case class for telling a MonitorActor that an
 * event has happened and at what path
 *
 * @param event WatchEvent.Kind[Path], one of ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE
 * @param path
 */
sealed case class EventAtPath(event: WatchEvent.Kind[_], path: Path)