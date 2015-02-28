Schwatcher [![Build Status](https://travis-ci.org/lloydmeta/schwatcher.png?branch=master)](https://travis-ci.org/lloydmeta/schwatcher)
==========

__Note__: Requires Java7 because the [WatchService API](http://docs.oracle.com/javase/7/docs/api/java/nio/file/WatchService.html)
is an essential part of this library.

__TL;DR__ A library that wraps the [WatchService API](http://docs.oracle.com/javase/7/docs/api/java/nio/file/WatchService.html).
of Java7. You can choose to send callbacks to be registered on paths (both
directories and files) _or_ turn path events into a composable asynchronous
data stream (RxScala Observable). For details, check out the Example usage section
below.

As of Java7, the WatchService API was introduced to allow developers to monitor the file system without resorting to an
external library/dependency like [JNotify](http://jnotify.sourceforge.net/). Unfortunately, it requires you to use a loop
that blocks in order to retrieve events from the API and does not have recursion support built-in.

Schwatcher will allow you to instantiate an Akka actor, register callbacks (functions that take a Path and return Unit) to be
fired for certain [Path](http://docs.oracle.com/javase/7/docs/api/java/nio/file/Path.html)s and [event types](http://docs.oracle.com/javase/7/docs/api/java/nio/file/StandardWatchEventKinds.html) by sending messages to it, then simply wait for the callbacks to be
fired. Alternatively, use the Observable interface to register for notifications on paths.

The goal of Schwatcher is to facilitate the use of the Java7 API in Scala in a simple way that is in line with the functional
programming paradigm.

__As of now, only Scala versions 2.10.x and 2.11.x are supported__

Installation
------------

Add the following to your `build.sbt`

```scala
libraryDependencies += "com.beachape.filemanagement" %% "schwatcher" % "0.1.6"
```

If the above does not work because it cannot be resolved, its likely because it hasn't been synced to Maven central yet.
In that case, download a SNAPSHOT release of the same version by adding this to `build.sbt`

```
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies += "com.beachape.filemanagement" %% "schwatcher" % "0.1.6-SNAPSHOT"
```

__Those using Akka 2.1.x__ should stay on 0.0.2 as 0.0.3 onwards uses Akka 2.2.x, which [requires some work to upgrade to](http://doc.akka.io/docs/akka/current/project/migration-guide-2.1.x-2.2.x.html).

__Those using Akka 2.2.x__ should stay on 0.0.9 as 0.0.10 onwards uses Akka 2.3.x, which [may require some work to upgrade to](http://doc.akka.io/docs/akka/2.3.0/project/migration-guide-2.2.x-2.3.x.html).

__0.1.0__ brings with it breaking changes on how to register callbacks (see example). Please be aware when upgrading from 0.0.x releases.


Example Usage
-------------

### Akka

The basic workflow is:

1. Instantiate a MonitorActor
2. Register callbacks by sending RegisterCallback messages to the MonitorActor
3. Carry on

* Optionally, you can un-register _all_ callbacks for a path and event type by sending UnRegisterCallback messages.
* Both RegisterCallback and UnRegisterCallback messages have a recursive argument (be sure to see point 5 in the
  [Caveats section](#caveats)) that triggers recursive registration and un-registration of callbacks for path.


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
  None,
  recursive = false,
  path = desktopFile,
  modifyCallbackFile)

/*
  If desktopFile is modified, this will also receive a callback
  it will receive callbacks for everything under the desktop directory
*/
fileMonitorActor ! RegisterCallback(
  ENTRY_MODIFY,
  None,
  recursive = false,
  path = desktop,
  modifyCallbackDirectory)


//modify a monitored file
val writer = new BufferedWriter(new FileWriter(desktopFile.toFile))
writer.write("Theres text in here wee!!")
writer.close

// #=> Something was modified in a file: /Users/lloyd/Desktop/test.txt
//     Something was modified in a directory: /Users/lloyd/Desktop/test.txt
```

### RxScala

There is another way to monitor files supported by this library: via Rx Observables.

This is neat because it allows you to treat notifications for files as, well, an Observable,
which conceptually is like a Stream, or a Go channel. You can filter on the stream, map on it,
filter on it ... basically compose it as you would a normal observable.

Note that since RxMonitor is powered by an Akka actor under the covers, the changes to
what you (do not) want to monitor are done in a thread-safe manner so feel free to pass
this object around.

```scala
import com.beachape.filemanagement.RxMonitor
import java.io.{FileWriter, BufferedWriter}

import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds._

val monitor = RxMonitor()
val observable = monitor.observable

val subscription = observable.subscribe(
  onNext = { p => println(s"Something was modified in a file mufufu: $p")},
  onError = { t => println(t)},
  onCompleted = { () => println("Monitor has been shut down") }
)

val desktopFile = Paths get "/Users/lloyd/Desktop/test"

monitor.registerPath(ENTRY_MODIFY, desktopFile)

Thread.sleep(100)

//modify a monitored file
val writer = new BufferedWriter(new FileWriter(desktopFile.toFile))
writer.write("Theres text in here wee!!")
writer.close

// #=> Something was modified in a file mufufu: /Users/lloyd/Desktop/test

// stop monitoring
monitor.stop()

// #=> Monitor has been shut down
```

Caveats
-------

__Above all things__ understand that library relies on the Java7 WatchService API and has the same constraints, limitations,
and behaviours according to the implementation for the version of JVM you're using.

Additional library-specific caveats and notes are:

1. Callbacks are registered for specific paths, and for directory paths, can be registered as recursive so that a single
   callback is fired when an event occurs inside the directory tree.
2. Callbacks are not checked for uniqueness when registered to a specific path.
3. A specific path can have multiple callbacks registered to a file [event types](http://docs.oracle.com/javase/7/docs/api/java/nio/file/StandardWatchEventKinds.html),
   all will be fired.
4. Callbacks are not guaranteed to be fired in any particular order unless if concurrency is set to 1 on the MonitorActor.
5. Callbacks that are registered with `recursive=true` are not _persistently-recursive_. That is, they do not propagate
   to new files or folders created/deleted after registration. Currently, the plan is to have developers handle this themselves
   in the callback functions.
6. Any event on a file path will bubble up to its immediate parent folder path. This means that if both a file and it's
   parent directory are registered for callbacks, both sets of callbacks will be fired.

As a result of note 6, you may want to think twice about registering recursive callbacks for `ENTRY_DELETE` because if a
directory is deleted within a directory, 2 callbacks will be fired, once for the path registered for the deleted directory
and once for the path registered for the directory above it (its parent directory).

Contributors
------------
- [Crdueck](https://github.com/crdueck)
- [georgeOsdDev](https://github.com/georgeOsdDev)

IDE Sponsor
-----------

Jetbrains sponsors this project through their Open Source IntelliJ licence, which has been an immensely helpful tool for
all of my Scala development, at home and at work.

[![IntelliJ](https://www.jetbrains.com/idea/docs/logo_intellij_idea.png)](https://www.jetbrains.com/idea/)

Licence
------

The MIT License (MIT)

Copyright (c) 2014 by Lloyd Chan

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
