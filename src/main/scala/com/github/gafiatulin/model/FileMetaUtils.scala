package com.github.gafiatulin.model

import com.github.gafiatulin.util.{Config, ErrorResponse}
import slick.jdbc.JdbcBackend._

import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * Created by victor on 18/07/16.
  */
trait FileMetaUtils extends FilesTable with Config {
  val db: Database
  implicit val ec: ExecutionContextExecutor

  import driver.api._

  def persistFileMeta(meta: FileMeta): Future[Long] = db.run(((files returning files.map(_.id)) += meta).transactionally)

  def queryFileMeta(params: Seq[(String, String)]): Future[Seq[FileMeta]] = {
    val query = files.filter{x =>
      params.flatMap{
        case ("id", v) => Some(x.id === v.toLong)
        case ("name", v) => Some(x.name === v)
        case ("media", v) => Some(x.media === v)
      }.+:(x.available === true).reduceLeft(_ && _)
    }
    db.run(query.result)
  }

  def deleteExisting(id: Long): Future[Unit] = {
    def del() = db.run(files.filter(_.id === id).delete.transactionally).map {
      case 0 | 1 => ()
      case _ => throw ErrorResponse("Deleted more than one file. This shouldn't happen")
    }
    db.run(files.filter(_.id === id).result.headOption).flatMap{
      case None => Future.successful(())
      case Some(a @ FileMeta(_, name, _, _, available)) =>
        fsAdapter.deleteFileBy(name, id, available).flatMap(_ => del())
    }
  }
  def completeFileUpload(id: Long): Future[Unit] = db.run(files.filter(_.id === id).map(_.available).update(true).transactionally).map{
    case 1 => ()
    case _ => throw ErrorResponse("Made available more than one file. This shouldn't happen")
  }
}
