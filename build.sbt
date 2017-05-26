lazy val akkaHttpVersion = "10.0.6"
lazy val akkaVersion     = "2.5.1"

lazy val root = (project in file("."))
  .settings(
    inThisBuild(
      List(
        organization := "bezh",
        scalaVersion := "2.12.2"
      )),
    name := "scala-tweets-analyzer",
    libraryDependencies ++= Seq(
      "org.scalatest"       %% "scalatest" % "3.0.1" % Test,
      "com.github.scopt"    %% "scopt"     % "3.5.0",
      "com.danielasfregola" %% "twitter4s" % "5.1"
    ),
    resolvers += Resolver.sonatypeRepo("relases"),
    fork in Test := true,
    fork in Compile := true
  )
  .enablePlugins(JavaAppPackaging)
