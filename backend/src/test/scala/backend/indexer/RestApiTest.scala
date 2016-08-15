package backend.indexer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

import scala.concurrent.Await
import scala.concurrent.Promise

import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import org.apache.jena.query.ResultSetFactory
import org.apache.jena.query.ResultSetFormatter

import akka.actor.Cancellable
import akka.http.javadsl.model.headers.RawRequestURI
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.testkit.RouteTest
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.testkit.TestFrameworkInterface

import backend.AkkaLogging
import backend.CustomContentTypes
import backend.Log4jRootLogging
import backend.PlatformConstants
import backend.WebService

trait RestApiTest extends TestFrameworkInterface with RouteTest with AkkaLogging with Log4jRootLogging {
  import backend.TestUtils._

  override def failTest(msg: String): Nothing = {
    throw new RuntimeException(msg)
  }

  override def testConfigSource = s"""
    akka {
      loglevel = INFO

      # We need to access the raw URIs because they are used as keys in the index.
      http.server.raw-request-uri-header = on
    }
    app {
      interface = "localhost"
      port = 7777
      test-mode = true
      forward-internal-logger-to-akka-logger = true

      storage {
        location = "$binDir/amora"
        index-dataset = "$binDir/amora/dataset"
        artifact-repo = "$binDir/amora/repo"
      }
    }
  """

  private def binDir =
    getClass.getClassLoader.getResource(".").getPath

  implicit val timeout = {
    import scala.concurrent.duration._
    // wait for the time that is available to the server + some more
    RouteTestTimeout(PlatformConstants.timeout.duration + 500.millis)
  }

  val service = new WebService
  val route = service.route

  case class Data(varName: String, value: String)

  def resultSetAsData(r: ResultSet): Seq[Seq[Data]] = {
    transformResultSet(r) { (v, q) ⇒
      val res = q.get(v)
      require(res != null, s"The variable `$v` does not exist in the result set.")
      val value =
        if (res.isLiteral())
          res.asLiteral().getString
        else
          res.toString()
      Data(v, value)
    }
  }

  def transformResultSet[A](r: ResultSet)(f: (String, QuerySolution) ⇒ A): Seq[Seq[A]] = {
    import scala.collection.JavaConverters._
    val vars = r.getResultVars.asScala.toSeq

    for (q ← r.asScala.toSeq) yield
      for (v ← vars) yield
        f(v, q)
  }

  def scheduleReq(req: ⇒ HttpRequest)(f: ⇒ Boolean) = {
    import scala.concurrent.duration._
    val p = Promise[Unit]
    var cancellable: Cancellable = null
    try {
      cancellable = system.scheduler.schedule(100.millis, 100.millis) {
        req ~> route ~> check {
          val res = f
          if (res)
            p.success(())
        }
      }
      Await.ready(p.future, Duration.Inf)
    } finally {
      cancellable.cancel()
    }
  }

  def testReq(req: ⇒ HttpRequest)(f: ⇒ Unit) = {
    val r = req
    r ~> route ~> check {
      val isJsonResponse = r.header[Accept].flatMap(_.mediaRanges.headOption).exists {
        case m if m matches CustomContentTypes.`sparql-results+json` ⇒ true
        case _ ⇒ false
      }
      if (debugTests && status == StatusCodes.OK && isJsonResponse) {
        val r = respAsResultSet()
        log.info("response as query result:\n" + resultSetAsString(r))
      }
      f
    }
  }

  def resultSetAsString(r: ResultSet): String = {
    val s = new ByteArrayOutputStream
    ResultSetFormatter.out(s, r)
    new String(s.toByteArray(), "UTF-8")
  }

  def respAsString: String =
    Await.result(response.entity.dataBytes.runFold("")(_ + _.utf8String), timeout.duration)

  def respAsResultSet(): ResultSet = {
    val in = new ByteArrayInputStream(respAsString.getBytes)
    ResultSetFactory.makeRewindable(ResultSetFactory.fromJSON(in))
  }

  def post(uri: String, request: String, header: HttpHeader*) = {
    val u = Uri(uri)
    val r = HttpRequest(HttpMethods.POST, u, List(RawRequestURI.create(u.toRelative.toString)) ++ header, request)
    log.info(s"sending request: $r")
    r
  }

  def get(uri: String) = {
    val u = Uri(uri)
    val r = HttpRequest(HttpMethods.GET, u, List(RawRequestURI.create(u.toRelative.toString)))
    log.info(s"sending request: $r")
    r
  }

  def showAmoraIndexContent(limit: Int = 100): Unit = {
    require(limit > 0, "limit needs to be greater than zero")
    testReq(post("http://amora.center/sparql", s"""query=
      select * where {
        ?s ?p ?o .
        filter regex(str(?s), "^http://amora.center/kb/")
      }
      limit $limit
    """, header = Accept(CustomContentTypes.`sparql-results+json`))) {
      status === StatusCodes.OK
    }
  }
}
