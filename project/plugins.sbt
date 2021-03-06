addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.7")
addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.2.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-js-engine" % "1.1.4")
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.1.0")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.2")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC1")

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
