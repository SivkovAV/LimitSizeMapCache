// ---------------------------------------------------------------------------
// Commands

addCommandAlias("ci", ";project root ;scalafmtCheckAll ;compile ;test ;package")

// ---------------------------------------------------------------------------
// Dependencies

val MonixVersion = "3.2.2"
val CatsVersion = "2.1.1"
val CatsEffectVersion = "2.1.4"
val SimulacrumVersion = "1.0.0"
val MacroParadiseVersion = "2.1.1"
val ScalaTestVersion = "3.2.0"
val ScalaTestPlusVersion = "3.2.0.0"
val ScalaCheckVersion = "1.14.3"
val KindProjectorVersion = "0.11.0"
val BetterMonadicForVersion = "0.3.1"
val SilencerVersion = "1.7.0"
val ParallelCollectionsVersion = "1.0.4"

/**
  * Defines common plugins between all projects.
  */
def defaultPlugins: Project => Project =
  pr => {
    pr.enablePlugins(AutomateHeaderPlugin)
      .enablePlugins(GitBranchPrompt)
  }

lazy val sharedSettings = Seq(
  organization := "RChain",
  scalaVersion := "2.13.3",

  headerLicense := Some(
    HeaderLicense.Custom(
      s"""|Copyright (c) 2020 Aleksei Sivkov.
          |All rights reserved.
          |""".stripMargin
    )
  ),
  //
  // Turning off fatal warnings for doc generation
  scalacOptions.in(Compile, doc) ~= filterConsoleScalacOptions,

  addCompilerPlugin("org.typelevel" % "kind-projector" % KindProjectorVersion cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % BetterMonadicForVersion),
  addCompilerPlugin("com.github.ghik" % "silencer-plugin" % SilencerVersion cross CrossVersion.full),

  // ---------------------------------------------------------------------------
  // Options for testing

  logBuffered in Test := false,
  logBuffered in IntegrationTest := false,
)

/**
  * Shared configuration across all sub-projects.
  */
def defaultProjectConfiguration(pr: Project) = {
  pr.configure(defaultPlugins)
    .settings(sharedSettings)
}

lazy val root = project
  .in(file("."))
  .aggregate(LimitSizeMapCache)
  .dependsOn(LimitSizeMapCache)
  .configure(defaultProjectConfiguration)
  .settings(
    mainClass in (Compile, run) := Some("stereo.rchain.limitsizemapcache.Main")
  )

lazy val LimitSizeMapCache = project
  .in(file("LimitSizeMapCache"))
  .configure(defaultProjectConfiguration)
  .settings(
    name := "LimitSizeMapCache",
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % MonixVersion,
      "org.typelevel" %% "simulacrum" % SimulacrumVersion % Provided,
      "org.typelevel" %% "cats-core" % CatsVersion,
      "org.typelevel" %% "cats-effect" % CatsEffectVersion,
      "org.scala-lang.modules" %% "scala-parallel-collections" % ParallelCollectionsVersion,
      // For testing
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
      "org.scalatestplus" %% "scalacheck-1-14" % ScalaTestPlusVersion % Test,
      "org.scalacheck" %% "scalacheck" % ScalaCheckVersion % Test,
      "org.typelevel" %% "cats-laws" % CatsVersion % Test,
      "org.typelevel" %% "cats-effect-laws" % CatsEffectVersion % Test
    ),
  )

// Reloads build.sbt changes whenever detected
Global / onChangedBuildSource := ReloadOnSourceChanges
