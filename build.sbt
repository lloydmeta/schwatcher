name := "schwatcher"

version := "0.0.8-SNAPSHOT"

scalaVersion := "2.10.3"

crossScalaVersions := Seq("2.10.0", "2.10.1", "2.10.2", "2.10.3")

crossVersion := CrossVersion.binary

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.9.1" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.3" % "test",
  "com.typesafe.akka" %% "akka-agent" % "2.2.3"
)

publishTo <<= version { v: String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

organization := "com.beachape.filemanagement"

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>https://github.com/lloydmeta/schwatcher</url>
  <licenses>
    <license>
      <name>MIT</name>
      <url>http://opensource.org/licenses/MIT</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:lloydmeta/schwatcher.git</url>
    <connection>scm:git:git@github.com:lloydmeta/schwatcher.git</connection>
  </scm>
  <developers>
    <developer>
      <id>lloydmeta</id>
      <name>Lloyd Chan</name>
      <url>http://beachape.com</url>
    </developer>
  </developers>
)
