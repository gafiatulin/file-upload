package com.github.gafiatulin

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.github.gafiatulin.util.Migration
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
  * Created by victor on 18/07/16.
  */
object Main extends App with Service with Migration {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val log: LoggingAdapter = system.log
  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)

  val driver = slick.jdbc.PostgresProfile
  lazy val db = Database.forConfig("database")

  private def reportAndTerminate(msg: String, cause: Option[Throwable] = None) = {
    cause match{
      case Some(t) => log.error(t, msg)
      case None => log.error(msg)
    }
    log.error("Exiting")
    system.terminate
  }
  Try(migrate()) match {
    case Failure(t) =>
      reportAndTerminate("Migration failed", Some(t))
    case Success(false) =>
      reportAndTerminate("Migration failed")
    case Success(true) =>
      log.info("Migration succeeded")
      log.info(s"Binding service to $httpInterface:$httpPort")
      Http().bindAndHandle(routes, httpInterface, httpPort).onComplete {
        case Success(sb) =>
          log.info("Binding succeeded")
          fsAdapter
        case Failure(t) =>
          reportAndTerminate("Binding failed", Some(t))
      }
  }
}
