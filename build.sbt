name := "schwatcher"

version := "0.3.5"

scalaVersion := "2.12.4"

crossScalaVersions := Seq("2.11.11", "2.12.4")

crossVersion := CrossVersion.binary

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

val akkaVersion = "2.5.6"

libraryDependencies ++= Seq(
  "org.scalatest"     %% "scalatest"    % "3.0.0" % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-actor"   % akkaVersion,
  "io.reactivex"      %% "rxscala"      % "0.26.5"
)

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

organization := "com.beachape.filemanagement"

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ =>
  false
}

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
      <url>https://beachape.com</url>
    </developer>
  </developers>
)

scalacOptions ++= {
  val common = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8", // yes, this is 2 args
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-unchecked",
    "-Xfatal-warnings",
    "-Yno-adapted-args",
    "-Ywarn-dead-code", // N.B. doesn't work well with the ??? hole
    "-Ywarn-numeric-widen",
    "-Xfuture",
    "-Ywarn-unused-import" // 2.11 only
  )
  if (scalaVersion.value.startsWith("2.12")) {
    common :+ "-Xlint:-unused,_"
  } else {
    common :+ "-Xlint"
  }
}