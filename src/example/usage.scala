import akka.actor.ActorSystem
import com.beachape.filemanagement.MonitorActor
import com.beachape.filemanagement.RegistryTypes._
import com.beachape.filemanagement.Messages._

import java.io.{FileWriter, BufferedWriter}

import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds._

implicit val system = ActorSystem("actorSystem")
val fileMonitorActor = system.actorOf(MonitorActor(concurrency = 2))

val modifyCallbackFile: Callback = {
  path => println(s"Something was modified in a file: $path")
}
val modifyCallbackDirectory: Callback = {
  path => println(s"Something was modified in a directory: $path")
}

val desktop = Paths get "/Users/lloyd/Desktop"
val desktopFile = Paths get "/Users/lloyd/Desktop/test"

/*
  This will receive callbacks for just the one file
 */
fileMonitorActor ! RegisterCallback(
  ENTRY_MODIFY,
  modifier = None,
  recursive = false,
  path = desktopFile,
  modifyCallbackFile)

/*
  If desktopFile is modified, this will also receive a callback
  it will receive callbacks for everything under the desktop directory
*/
fileMonitorActor ! RegisterCallback(
  ENTRY_MODIFY,
  modifier = None,
  recursive = false,
  path = desktop,
  modifyCallbackDirectory)


//modify a monitored file
val writer = new BufferedWriter(new FileWriter(desktopFile.toFile))
writer.write("Theres text in here wee!!")
writer.close()

// #=> Something was modified in a file: /Users/a13075/Desktop/test.txt
//     Something was modified in a directory: /Users/a13075/Desktop/test.txt
