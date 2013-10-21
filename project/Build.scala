import sbt._
import Keys._

object ApplicationBuild extends Build {

  val main = Project(
    id = "irc-bot",
    base = file("."),
    settings = Defaults.defaultSettings ++ Seq(
      organization := "org.w3",
      version      := "0.1-SNAPSHOT",
      scalaVersion := "2.10.3",
      scalacOptions ++= Seq("-deprecation", "-unchecked", "-optimize", "-feature", "-language:implicitConversions,higherKinds", "-Xmax-classfile-name", "140", "-Yinline-warnings"),
      resolvers += "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
      resolvers += "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/",
      resolvers += "Typesafe Repository snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
      libraryDependencies += "org.scalatest" %% "scalatest" % "2.0.M6-SNAP35" % "test",
      libraryDependencies +=  "com.typesafe.akka" %% "akka-actor" % "2.2.1"
    )
  )

}
