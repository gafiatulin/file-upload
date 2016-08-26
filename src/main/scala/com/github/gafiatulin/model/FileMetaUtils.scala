package com.github.gafiatulin.model

import java.sql.SQLException

import akka.NotUsed
import akka.http.scaladsl.model.StatusCodes
import akka.stream.scaladsl.Source
import com.github.gafiatulin.util.{Config, ErrorResponse}
import slick.jdbc.JdbcBackend._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

/**
  * Created by victor on 18/07/16.
  */
trait FileMetaUtils extends FilesTable with Config {
  val db: Database
  implicit val ec: ExecutionContextExecutor

  import driver.api._

  def persistFileMeta(meta: FileMeta): Future[Long] = db.run(((files returning files.map(_.id)) += meta).transactionally.asTry).map{
    case Success(id) => id
    case Failure(t: SQLException) if t.getSQLState == "23505" =>
      throw ErrorResponse("File with specified name already exists or it's upload have been requested", StatusCodes.BadRequest)
    case Failure(t) => throw ErrorResponse(t.getMessage)
  }

  def queryFileMeta(params: Seq[(String, String)]): Source[FileMeta, NotUsed] = {
    val query = files.filter{x =>
      params.flatMap{
        case ("id", v) => Some(x.id === v.toLong)
        case ("name", v) => Some(x.name.startsWith(v))
        case ("media", v) => Some(x.media.startsWith(v))
      }.+:(x.available === true).reduceLeft(_ && _)
    }
    Source.fromPublisher(db.stream(query.result))
  }

  def deleteExisting(id: Long): Future[Unit] = {
    def del() = db.run(files.filter(_.id === id).delete.transactionally).map {
      case 0 | 1 => ()
      case _ => throw ErrorResponse("Deleted more than one file. This shouldn't happen")
    }
    db.run(files.filter(_.id === id).result.headOption).flatMap{
      case None => Future.successful(())
      case Some(a @ FileMeta(_, name, _, _, _, available)) =>
        fsAdapter.deleteFileBy(name, id, available).flatMap(_ => del())
    }
  }

  def allUnavailable: Source[FileMeta, NotUsed] = Source.fromPublisher(db.stream(files.filter(_.available === false).result))

  def completeFileUpload(id: Long): Future[Unit] = db.run(files.filter(_.id === id).map(_.available).update(true).transactionally).map{
    case 1 => ()
    case _ => throw ErrorResponse("Made available more than one file. This shouldn't happen")
  }
}
