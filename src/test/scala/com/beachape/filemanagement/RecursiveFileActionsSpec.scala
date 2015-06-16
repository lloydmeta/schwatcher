package com.beachape.filemanagement

import java.nio.file.Files
import org.scalatest._

class RecursiveFileActionsSpec extends FunSpec
    with Matchers
    with BeforeAndAfter {

  val dummy = new RecursiveFileActions {}

  val tempDirPath = Files.createTempDirectory("root")
  val tempDirLevel1Path = Files.createTempDirectory(tempDirPath, "level1")
  val tempDirLevel2Path = Files.createTempDirectory(tempDirLevel1Path, "level2")
  val tempFileInTempDir = Files.createTempFile(tempDirPath, "hello", ".there")
  tempDirPath.toFile.deleteOnExit()
  tempDirLevel1Path.toFile.deleteOnExit()
  tempDirLevel2Path.toFile.deleteOnExit()
  tempFileInTempDir.toFile.deleteOnExit()

  describe("#forEachDir") {

    it("should ignore paths for files") {
      var called = false
      dummy.forEachDir(tempFileInTempDir) { _ => called = true }
      called should be(false)
    }

    it("should call the block once for every existing directory inside a directory path") {
      var counter = 0
      dummy.forEachDir(tempDirPath) { _ => counter += 1 }
      counter should be(3)
    }
  }

}
