package com.beachape.filemanagement

import java.nio.file.{FileVisitResult, SimpleFileVisitor, Files, Path}
import java.nio.file.attribute.BasicFileAttributes
import scala.reflect.io.File
import com.typesafe.scalalogging.slf4j.Logging

/**
 * Trait for allowing a block of code to be run recursively given a directory path
 */
trait RecursiveFileActions extends Logging {

  /**
   * Recursively performs an action given a directory path
   *
   * Ignores all paths that are not directories. Uses the Java 7 API to walk
   * a directory tree
   *
   * @param path Path object to a directory
   * @param doBlock a function that takes Path and BasicFileAttributes as parameters and returns Unit
   * @return Unit
   */
  def forEachDir(path: Path)(doBlock: (Path, BasicFileAttributes) => Unit) {
    if (File(path.toString).isDirectory)
      Files.walkFileTree(path, new SimpleFileVisitor[Path] {
        override def preVisitDirectory(dir: Path, attributes: BasicFileAttributes) = {
          doBlock(dir, attributes)
          FileVisitResult.CONTINUE
        }
      })
    else
      logger.debug(s"Path '$path' is not a directory, skipping recursive action")
  }
}
