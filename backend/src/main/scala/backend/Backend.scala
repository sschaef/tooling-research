package backend

import scala.util.Failure
import scala.util.Success

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

object Backend extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val config = system.settings.config
  val interface = config.getString("app.interface")
  val port = config.getInt("app.port")

  val service = new WebService

  val binding = Http().bindAndHandle(service.route, interface, port)
  binding.onComplete {
    case Success(binding) ⇒
      val addr = binding.localAddress
      system.log.info(s"Server is listening on ${addr.getHostName}:${addr.getPort}")
    case Failure(e) ⇒
      system.log.error(e, "Failed to start server")
      system.shutdown()
  }
}