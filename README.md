Schwatcher
==========

__Note__: Requires Java 7 because the [WatchService API](http://docs.oracle.com/javase/7/docs/api/java/nio/file/WatchService.html) is an essential part of this library.

In short, a library that wraps the [WatchService API](http://docs.oracle.com/javase/7/docs/api/java/nio/file/WatchService.html) of Java 7 and allows callbacks to be registered on both directories and files.

_Maven_ installation support is coming.

Note:

1. Callbacks are registered for specific paths and for directory paths can be registered as recursive so that a single callback is fired when an event occurs inside the directory tree.
2. Callbacks are not checked for uniqueness when registered to a specific path.
3. A specific path can have multiple callbacks, all will be fired.
4. Callbacks are not guaranteed to be fired in any particular order unless if concurrency is set to 1 on the MonitorActor.
5. Any event on a file path will bubble up to its immediate folder path. This means that if both a file and it's parent directory are registered for callbacks, both set of callbacks will be fired.

Example Usage
-------------

```scala
import akka.actor.ActorSystem
import com.beachape.filemanagement.MonitorActor
import com.beachape.filemanagement.RegistryTypes._
import com.beachape.filemanagement.Messages._

import java.io.{FileWriter, BufferedWriter}

import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds._

implicit val system = ActorSystem("actorSystem")
val fileMonitorActor = system.actorOf(MonitorActor(concurrency = 2))

val modifyCallbackFile: Callback = { path => println(s"Something was modified in a file: $path")}
val modifyCallbackDirectory: Callback = { path => println(s"Something was modified in a directory: $path")}

val desktop = Paths get "/Users/lloyd/Desktop"
val desktopFile = Paths get "/Users/lloyd/Desktop/test"

/*
  This will receive callbacks for just the one file
 */
fileMonitorActor ! RegisterCallback(
  ENTRY_MODIFY,
  recursive = false,
  path = desktopFile,
  modifyCallbackFile)

/*
  If desktopFile is modified, this will also receive a callback
  it will receive callbacks for everything under the desktop directory
*/
fileMonitorActor ! RegisterCallback(
  ENTRY_MODIFY,
  recursive = false,
  path = desktop,
  modifyCallbackDirectory)


//modify a monitored file
val writer = new BufferedWriter(new FileWriter(desktopFile.toFile))
writer.write("Theres text in here wee!!")
writer.close

// #=> Something was modified in a file: /Users/a13075/Desktop/test.txt
//     Something was modified in a directory: /Users/a13075/Desktop/test.txt
```

## License

Copyright (c) 2013 by Lloyd Chan

Permission is hereby granted, free of charge, to any person obtaining a
copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, and to permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be included
in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
