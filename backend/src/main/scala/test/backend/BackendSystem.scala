package test.backend

import java.nio.ByteBuffer

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import shared.test._

final class BackendSystem(implicit system: ActorSystem) {

  import boopickle.Default._
  val persons = Seq(Person("myname", 50), Person("anothername", 26))
  def personBuf = Pickle.intoBytes(persons)

  val actor = system.actorOf(Props(new Actor {
    val repl = new Repl
    var clients = Map.empty[String, ActorRef]

    override def receive = {
      case NewClient(subject) ⇒
        val sender = "client" + clients.size
        println(s"New client '$sender' seen")
        subject ! ConnectionSuccessful(sender)

      case ClientReady(sender, subject) ⇒
        if (clients.contains(sender)) {
          println(s"'$sender' already exists")
          // TODO this can only happen when multiple clients try to join at nearly the same moment
          subject ! ConnectionFailure
        }
        else {
          context.watch(subject)
          clients += sender → subject
          println(s"'$sender' joined")
          subject ! ConnectionSuccessful(sender)
        }

      case ReceivedMessage(sender, msg) ⇒
        println(s"'$sender' sent '$msg'")
        msg match {
          case Interpret(id, expr) =>
            val res = repl.interpret(expr)
            clients(sender) ! InterpretedResult(id, res)
          case msg ⇒
            clients(sender) ! PersonList(persons)
        }

      case ClientLeft(sender) ⇒
        clients -= sender
        println(s"'$sender' left")
    }
  }))

  def authFlow(): Flow[ByteBuffer, ByteBuffer, Unit] = {
    val out = Source
      .actorRef[Response](1, OverflowStrategy.fail)
      .mapMaterializedValue { actor ! NewClient(_) }
      .map(Pickle.intoBytes(_))
    Flow.wrap(Sink.ignore, out)(Keep.none)
  }

  def messageFlow(sender: String): Flow[ByteBuffer, ByteBuffer, Unit] = {
    def sink(sender: String) = Sink.actorRef[Msg](actor, ClientLeft(sender))

    val in = Flow[ByteBuffer]
      .map(b ⇒ ReceivedMessage(sender, Unpickle[Request].fromBytes(b)))
      .to(sink(sender))
    val out = Source
      .actorRef[Response](1, OverflowStrategy.fail)
      .mapMaterializedValue { actor ! ClientReady(sender, _) }
      .map(Pickle.intoBytes(_))
    Flow.wrap(in, out)(Keep.none)
  }
}

sealed trait Msg
case class ReceivedMessage(sender: String, req: Request) extends Msg
case class ClientLeft(sender: String) extends Msg
case class NewClient(subject: ActorRef) extends Msg
case class ClientReady(sender: String, subject: ActorRef) extends Msg
