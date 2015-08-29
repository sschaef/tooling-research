package backend

import java.nio.ByteBuffer

import scala.util.Failure
import scala.util.Success

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import nvim._
import protocol.{Mode ⇒ _, _}

final class NvimAccessor(implicit system: ActorSystem) {
  import system.dispatcher

  private val nvim = new Nvim(new Connection("127.0.0.1", 6666))
  // we cache window here in order to reduce communication overhead
  private val window = nvim.currentWindow
  // set ruler for better debugging purposes
  nvim.sendVimCommand(":set ruler")

  private def currentBufferContent = for {
    b ← nvim.currentBuffer
    count ← b.lineCount
    s ← b.lineSlice(0, count)
  } yield s

  def handleClientJoined(sender: String, self: ActorRef): Unit = {
    val resp = for {
      win ← window
      cursor ← win.cursor
      content ← currentBufferContent
      mode ← nvim.activeMode
    } yield ClientUpdate(None, Mode.asString(mode), content, cursor.row-1, cursor.col)

    resp onComplete {
      case Success(resp) ⇒
        self ! NvimSignal(sender, resp)
        system.log.info(s"sent: $resp")

      case Failure(f) ⇒
        system.log.error(f, s"Failed to send an update to the client '$sender'.")
    }
  }

  def handleTextChange(change: TextChange, sender: String, self: ActorRef): Unit = {
    system.log.info(s"received: $change")
    val resp = for {
      _ ← nvim.sendInput(change.text)
      win ← window
      cursor ← win.cursor
      content ← currentBufferContent
    } yield TextChangeAnswer(change.bufferRef, content, cursor.row-1, cursor.col)

    resp onComplete {
      case Success(resp) ⇒
        self ! NvimSignal(sender, resp)
        system.log.info(s"sent: $resp")

      case Failure(f) ⇒
        system.log.error(f, s"Failed to send response after client request `$change`.")
    }
  }

  def handleSelectionChange(change: SelectionChange, sender: String, self: ActorRef): Unit = {
    system.log.info(s"received: $change")
    val resp = for {
      win ← window
      _ ← win.cursor = Position(change.cursorRow+1, change.cursorColumn)
    } yield SelectionChangeAnswer(change.bufferRef, change.cursorRow, change.cursorColumn, change.cursorRow, change.cursorColumn)

    resp onComplete {
      case Success(resp) ⇒
        self ! NvimSignal(sender, resp)
        system.log.info(s"sent: $resp")

      case Failure(f) ⇒
        system.log.error(f, s"Failed to send response after client request `$change`.")
    }
  }

  def handleControl(control: Control, sender: String, self: ActorRef): Unit = {
    system.log.info(s"received: $control")
    val resp = for {
      _ ← nvim.sendInput(control.controlSeq)
      win ← window
      cursor ← win.cursor
      content ← currentBufferContent
      mode ← nvim.activeMode
    } yield ClientUpdate(Some(control.bufferRef), Mode.asString(mode), content, cursor.row-1, cursor.col)

    resp onComplete {
      case Success(resp) ⇒
        self ! NvimSignal(sender, resp)
        system.log.info(s"sent: $resp")

      case Failure(f) ⇒
        system.log.error(f, s"Failed to send response after client request `$control`.")
    }
  }
}

final class BackendSystem(implicit system: ActorSystem) {
  import boopickle.Default._

  private val actor = system.actorOf(Props[MsgActor])

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

final class MsgActor extends Actor {
  import context.system

  private val repl = new Repl
  private var clients = Map.empty[String, ActorRef]
  private val nvim = new NvimAccessor

  override def receive = {
    case NewClient(subject) ⇒
      val sender = "client" + clients.size
      system.log.info(s"New client '$sender' seen")
      subject ! ConnectionSuccessful(sender)

    case ClientReady(sender, subject) ⇒
      if (clients.contains(sender)) {
        system.log.info(s"'$sender' already exists")
        // TODO this can only happen when multiple clients try to join at nearly the same moment
        subject ! ConnectionFailure
      }
      else {
        context.watch(subject)
        clients += sender → subject
        system.log.info(s"'$sender' joined")
        subject ! ConnectionSuccessful(sender)
        nvim.handleClientJoined(sender, self)
      }

    case ReceivedMessage(sender, msg) ⇒
      msg match {
        case Interpret(id, expr) ⇒
          val res = repl.interpret(expr)
          clients(sender) ! InterpretedResult(id, res)

        case change: SelectionChange ⇒
          nvim.handleSelectionChange(change, sender, self)

        case change: TextChange ⇒
          nvim.handleTextChange(change, sender, self)

        case control: Control ⇒
          nvim.handleControl(control, sender, self)
      }

    case NvimSignal(sender, resp) ⇒
      clients(sender) ! resp

    case ClientLeft(sender) ⇒
      clients -= sender
      system.log.info(s"'$sender' left")
  }
}

sealed trait Msg
case class ReceivedMessage(sender: String, req: Request) extends Msg
case class ClientLeft(sender: String) extends Msg
case class NewClient(subject: ActorRef) extends Msg
case class ClientReady(sender: String, subject: ActorRef) extends Msg
case class NvimSignal(sender: String, resp: Response)