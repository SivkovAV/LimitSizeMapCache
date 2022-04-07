ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.12.11"

lazy val root = (project in file("."))
  .settings(
    name := "mapCache",
    idePackagePrefix := Some("stereo.rchain.mapcache")
  )

scalaVersion := "2.12.11"

libraryDependencies +=
  "org.typelevel" %% "cats-core" % "2.7.0"

version := "3.3.9"

scalaVersion := "2.13.6"

libraryDependencies += "org.typelevel" %% "cats-effect" % "3.3.9"


scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:postfixOps"
)