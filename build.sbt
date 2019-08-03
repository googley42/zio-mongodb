name := "zio-mongodb"

version := "0.1"

scalaVersion := "2.12.8"

val ZioVersion = "1.0.0-RC10-1"

libraryDependencies += "org.mongodb" % "mongodb-driver-async" % "3.8.0"
libraryDependencies += "dev.zio" %% "zio" % ZioVersion
libraryDependencies += "dev.zio" %% "zio-streams" % ZioVersion
