package com.beachape.filemanagement

import com.beachape.filemanagement.RegistryTypes._
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{ FileVisitResult, SimpleFileVisitor, Files, Path }

/**
 * Trait for allowing a block of code to be run recursively given a directory path
 */
trait RecursiveFileActions {

  /**
   * Recursively performs an action given a directory path
   *
   * Ignores all paths that are not directories. Uses the Java 7 API to walk
   * a directory tree
   *
   * @param path Path object to a directory
   * @param callback Callback to perform on each subdirectory path
   * @return Unit
   */
  def forEachDir(path: Path)(callback: Callback) = {
    if (path.toFile.isDirectory)
      Files.walkFileTree(path, new SimpleFileVisitor[Path] {
        override def preVisitDirectory(dir: Path, attributes: BasicFileAttributes) = {
          callback(dir)
          FileVisitResult.CONTINUE
        }
      })
  }
}
