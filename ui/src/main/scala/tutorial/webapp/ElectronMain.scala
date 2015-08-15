package tutorial.webapp

import scala.scalajs.js.JSApp
import scala.scalajs.js.Dynamic.{global => jsg}
import io.atom.electron._

object ElectronMain extends JSApp {

  override def main(): Unit = {
    // Module to control application life
    val app = jsg.require("app").asInstanceOf[App]

    // Report crashes to our server
    jsg.require("crash-reporter").start()

    // Keep a global reference of the window object. If we don't, the window will
    // be closed automatically when the JavaScript object is GCed.
    var w: BrowserWindow = null

    app.on("ready", () => {
      w = BrowserWindow(width = 800, height = 600)
      // TODO don't hardcode URL and port here
      w.loadUrl("http://localhost:9999")
      w.openDevTools()
      w.on("closed", () => {
        w = null
      })

      ()
    })
  }
}
