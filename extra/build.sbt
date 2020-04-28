name := "odinson-extra"

libraryDependencies ++= {

  val procVersion = "7.4.4"

  Seq(
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "ai.lum"     %% "nxmlreader"            % "0.1.2",
    "ai.lum"     %% "labrador-core"         % "0.0.2-SNAPSHOT",
  )

}
