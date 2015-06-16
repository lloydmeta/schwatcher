package com.beachape.filemanagement

import java.nio.file.Path

object RegistryTypes {
  type Callback = (Path) => Unit
  type Callbacks = List[Callback]
}
