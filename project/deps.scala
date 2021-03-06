import sbt._
import sbt.Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object deps {
  // https://github.com/lihaoyi/scalatags
  val scalatags       = "0.5.2"
  // https://github.com/lloydmeta/enumeratum
  val enumeratum      = "1.3.7"
  val akka            = "2.5.1"
  val akkaHttp        = "10.0.5"
  val jena            = "3.0.1"
  // https://github.com/scalaj/scalaj-http
  val scalaj          = "2.3.0"
  // https://github.com/typesafehub/scala-logging
  val scalaLogging    = "3.1.0"
  // https://github.com/ochrons/boopickle
  val boopickle       = "1.1.0"
  // https://github.com/msgpack4z/msgpack4z-core
  val msgpack4zCore   = "0.3.7"
  // https://github.com/msgpack4z/msgpack4z-java07
  val msgpack4zJava   = "0.3.5"
  // https://github.com/antonkulaga/codemirror-facade
  val codemirror      = "5.5-0.5"
  val jquery          = "0.8.0"
  val junitInterface  = "0.11"
  val coursier        = "1.0.0-RC1"

  lazy val protocol = Def.setting(Seq(
    "me.chrons"                      %%% "boopickle"                         % boopickle
  ))

  lazy val backend = Def.setting(Seq(
    "com.typesafe.akka"              %%  "akka-http-core"                    % akkaHttp,
    "com.typesafe.akka"              %%  "akka-http"                         % akkaHttp,
    "com.typesafe.akka"              %%  "akka-http-spray-json"              % akkaHttp,
    "com.typesafe.akka"              %%  "akka-stream"                       % akka,
    "com.typesafe.akka"              %%  "akka-slf4j"                        % akka,
    "com.lihaoyi"                    %%% "scalatags"                         % scalatags,
    "org.apache.jena"                %   "apache-jena-libs"                  % jena,
    "io.get-coursier"                %%  "coursier"                          % coursier,
    "io.get-coursier"                %%  "coursier-cache"                    % coursier,
    "org.scalaj"                     %%  "scalaj-http"                       % scalaj,
    "com.novocode"                   %   "junit-interface"                   % junitInterface   % "test",
    "com.typesafe.akka"              %%  "akka-http-testkit"                 % akkaHttp         % "test"
  ))

  lazy val nvim = Def.setting(Seq(
    "com.github.xuwei-k"             %%  "msgpack4z-core"                    % msgpack4zCore,
    "com.github.xuwei-k"             %   "msgpack4z-java"                    % msgpack4zJava,
    "com.beachape"                   %%  "enumeratum"                        % enumeratum,
    "org.scala-lang"                 %   "scala-reflect"                     % scalaVersion.value,
    "com.typesafe.scala-logging"     %%  "scala-logging"                     % scalaLogging
  ))

  lazy val sjs = Def.setting(Seq(
    "be.doeraene"                    %%% "scalajs-jquery"                    % jquery,
    "org.denigma"                    %%% "codemirror-facade"                 % codemirror,
    "com.lihaoyi"                    %%% "scalatags"                         % scalatags
  ))

  lazy val webUi = Def.setting(Seq(
    "be.doeraene"                    %%% "scalajs-jquery"                    % jquery,
    "com.lihaoyi"                    %%% "scalatags"                         % scalatags
  ))

  lazy val webjars = Def.setting(Seq(
    "org.webjars"                    %   "codemirror"                        % "5.5"                     / "codemirror.js",
    // https://github.com/chjj/marked
    "org.webjars.bower"              %   "marked"                            % "0.3.3"                   / "marked.js",
    "org.webjars"                    %   "d3js"                              % "3.5.5-1"                 / "d3.js",
    // https://github.com/fgnass/spin.js
    "org.webjars.bower"              %   "spin.js"                           % "2.3.1"                   / "spin.js"
  ))

  lazy val firefoxPlugin = Def.setting(Seq(
    "be.doeraene"                    %%% "scalajs-jquery"                    % jquery,
    "com.lihaoyi"                    %%% "scalatags"                         % scalatags
  ))

  lazy val scalacConverter = Def.setting(Seq(
    "org.scala-lang"                 %   "scala-compiler"                    % scalaVersion.value,
    "org.scala-refactoring"          %%  "org.scala-refactoring.library"     % "0.11.0"                  cross CrossVersion.full
  ))

  lazy val javacConverter = Def.setting(Seq(
    "org.ow2.asm"                    %   "asm-commons"                       % "5.0.4",
    "org.ow2.asm"                    %   "asm-util"                          % "5.0.4"
  ))

  lazy val dotcConverter = Def.setting(Seq(
    "ch.epfl.lamp"                   %%  "dotty"                             % "0.1-SNAPSHOT",
    "me.d-d"                         %   "scala-compiler"                    % "2.11.5-20160322-171045-e19b30b3cd"
  ))

  lazy val scalaCompilerService = Def.setting(Seq(
    "com.novocode"                   %   "junit-interface"                   % junitInterface   % "test"
  ))

  lazy val nlp = Def.setting(Seq(
    // https://github.com/extjwnl/extjwnl
    "net.sf.extjwnl"                 %   "extjwnl"                           % "1.9.1",
    "net.sf.extjwnl"                 %   "extjwnl-data-wn31"                 % "1.2",
    "org.parboiled"                  %%  "parboiled"                         % "2.1.3"
  ))

  lazy val amoraSbtPlugin = Def.setting(Seq(
    "io.get-coursier"                %%  "coursier"                          % coursier,
    "io.get-coursier"                %%  "coursier-cache"                    % coursier
  ))
}
