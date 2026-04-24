name := "web"

organization := "vortex"

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.13.12"

// Asset pipeline: digest (fingerprinting) → gzip (compression)
// Ensures browsers always load fresh CSS/JS after each deploy
pipelineStages := Seq(digest, gzip)

libraryDependencies ++= Seq(
  guice,
  jdbc, // Play JDBC API

  // Reactive Manifesto - Core message-driven
  "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",

  // Reactive Manifesto - Distribución (Issue #14: EventBus → DistributedPubSub)
  "com.typesafe.akka" %% "akka-cluster-typed"          % "2.8.5",
  "com.typesafe.akka" %% "akka-cluster-tools"          % "2.8.5",
  "com.typesafe.akka" %% "akka-serialization-jackson"  % "2.8.5",

  // Database - Slick (Reactive ORM)
  "org.playframework" %% "play-slick" % "6.1.1",
  "org.playframework" %% "play-slick-evolutions" % "6.1.1",
  
  // H2 Database (in-memory for development)
  "com.h2database" % "h2" % "2.2.224",
  
  // PostgreSQL Driver
  "org.postgresql" % "postgresql" % "42.7.1",

  // BCrypt for password hashing
  "org.mindrot" % "jbcrypt" % "0.4",

  // Email sending
  "com.sun.mail" % "javax.mail" % "1.6.2",

  // Markdown rendering (publicaciones dinámicas)
  "org.commonmark" % "commonmark" % "0.22.0",
  "org.commonmark" % "commonmark-ext-gfm-tables" % "0.22.0",
  "org.commonmark" % "commonmark-ext-autolink" % "0.22.0",

  // PostgreSQL Database (for production)
  "org.postgresql" % "postgresql" % "42.7.2",

  // Testing
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.0" % Test,
  "com.typesafe.akka"      %% "akka-actor-testkit-typed" % "2.8.5" % Test
)

// Seguridad ante conflictos transitivos
ThisBuild / evictionErrorLevel := Level.Warn
