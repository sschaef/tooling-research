package amora.backend.actors

import scala.util.Try

import org.apache.jena.query.ResultSetRewindable

import akka.actor.Actor
import akka.actor.ActorLogging
import amora.api.SparqlModel
import amora.api.Turtle
import amora.backend.Content
import amora.backend.Logger
import amora.backend.indexer.Indexer

class IndexerActor extends Actor with ActorLogging {

  import IndexerMessage._

  private def akkaLog = log

  private val logger = new Logger {
    override def debug(msg: String): Unit = akkaLog.debug(msg)
    override def warning(msg: String): Unit = akkaLog.warning(msg)
    override def info(msg: String): Unit = akkaLog.info(msg)
    override def error(msg: String, t: Throwable): Unit = akkaLog.error(t, msg)
    override def log = throw new UnsupportedOperationException
    override def logLevel = throw new UnsupportedOperationException
    override def logLevel_=(level: Logger.LogLevel) = throw new UnsupportedOperationException

    override def close() = throw new UnsupportedOperationException
    override def isClosed = false
  }
  private val indexer = new Indexer(Content.ModelName, logger)
  private val config = context.system.settings.config
  private val testMode = config.getBoolean("app.test-mode")
  private val dataset =
    if (testMode)
      indexer.mkInMemoryDataset
    else
      indexer.mkDataset(config.getString("app.storage.index-dataset"))

  log.info("Indexer created dataset at: " + (if (testMode) "<memory>" else config.getString("app.storage.index-dataset")))
  indexer.writeDataset(dataset)(indexer.startupIndexer)

  override def receive = {
    case RunQuery(query) ⇒
      sender ! Try(handleQuery(query))

    case RunUpdate(query) ⇒
      sender ! Try(handleUpdate(query))

    case RunConstruct(query) ⇒
      sender ! Try(handleConstruct(query))

    case RunTurtleUpdate(query) ⇒
      sender ! Try(handleTurtleUpdate(query))

    case RunNlq(query) ⇒
      sender ! Try(handleNlq(query))

    case GetHeadCommit ⇒
      sender ! Try(headCommit())

    case ListCommits ⇒
      sender ! Try(listCommits())

    case ShowCommit(commit) ⇒
      sender ! Try(showCommit(commit))
  }

  override def postStop() = {
    dataset.close()
  }

  def handleQuery(query: String): ResultSetRewindable = {
    log.info(s"Handle SPARQL query:\n$query")
    indexer.readDataset(dataset) { dataset ⇒
      indexer.withModel(dataset) { model ⇒
        indexer.withQueryService(model, query)
      }
    }
  }

  def handleConstruct(query: String): SparqlModel = {
    log.info(s"Handle SPARQL construct query:\n$query")
    indexer.readDataset(dataset) { dataset ⇒
      indexer.withModel(dataset) { model ⇒
        indexer.withConstructService(model, query)
      }
    }
  }

  def handleUpdate(query: String): Unit = {
    log.info(s"Handle SPARQL update:\n$query")
    indexer.writeDataset(dataset) { dataset ⇒
      indexer.withModel(dataset) { model ⇒
        indexer.withUpdateService(model, query)(_ ⇒ ())
      }
    }
  }

  def handleTurtleUpdate(query: String): Unit = {
    log.info(s"Handle Turtle update:\n$query")
    indexer.writeDataset(dataset) { dataset ⇒
      indexer.withModel(dataset) { model ⇒
        indexer.writeAs(dataset, model, Turtle, query)
      }
    }
  }

  def handleNlq(query: String): String = {
    log.info(s"Handle natural language query:\n$query")
    indexer.writeDataset(dataset) { dataset ⇒
      indexer.withModel(dataset) { model ⇒
        indexer.askNlq(model, query)
      }
    }
  }

  def headCommit(): String = {
    indexer.readDataset(dataset) { dataset ⇒
      indexer.withModel(dataset) { model ⇒
        indexer.headCommit(model).getOrElse("")
      }
    }
  }

  def listCommits(): List[String] = {
    indexer.readDataset(dataset) { dataset ⇒
      indexer.withModel(dataset) { model ⇒
        indexer.listCommits(model)
      }
    }
  }

  def showCommit(commit: String): SparqlModel = {
    indexer.readDataset(dataset) { dataset ⇒
      indexer.showCommit(dataset, commit)
    }
  }
}

sealed trait IndexerMessage
object IndexerMessage {
  case class RunQuery(query: String) extends IndexerMessage
  case class RunUpdate(query: String) extends IndexerMessage
  case class RunConstruct(query: String) extends IndexerMessage
  case class RunTurtleUpdate(query: String) extends IndexerMessage
  case class RunNlq(query: String) extends IndexerMessage
  case object GetHeadCommit extends IndexerMessage
  case object ListCommits extends IndexerMessage
  case class ShowCommit(commit: String) extends IndexerMessage
}
