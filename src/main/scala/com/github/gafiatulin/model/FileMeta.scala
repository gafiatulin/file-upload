package com.github.gafiatulin.model

import akka.http.scaladsl.model.{MediaType, MediaTypes, Uri}
import slick.driver.JdbcProfile
import slick.lifted.ProvenShape
import spray.json.{JsValue, JsonParser}

/**
  * Created by victor on 18/07/16.
  */
case class FileMeta(id: Option[Long], name: String, url: Option[Uri], media: Option[MediaType], extra: JsValue, available: Boolean = false)

object FileMeta{
  def toRow(obj: FileMeta): Option[(Option[Long], String, Option[String], Option[String], String, Boolean)] =
    Some((obj.id, obj.name, obj.url.map(_.toString), obj.media.map(_.toString), obj.extra.compactPrint, obj.available))
  def fromRow(fId: Option[Long], fName: String, fUrl: Option[String], fMedia: Option[String], fExtra: String, fAvailability: Boolean): FileMeta = {
    val media = fMedia.flatMap{ x =>
      val t = x.splitAt(x.indexOf('/'))
      MediaTypes.getForKey(t._1 -> t._2.tail)
    }
    FileMeta(fId, fName, fUrl.map(Uri(_)), media, JsonParser(fExtra), fAvailability)
  }
}

trait FilesTable{
  val driver: JdbcProfile
  import driver.api._
  class Files(tag: Tag) extends Table[FileMeta](tag, "files"){
    def id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    def name: Rep[String] = column[String]("name")
    def url: Rep[String] = column[String]("url")
    def media: Rep[String] = column[String]("media")
    def extra: Rep[String] = column[String]("extra", O.SqlType("TEXT"))
    def available: Rep[Boolean] = column[Boolean]("available")
    def * : ProvenShape[FileMeta] = (id.?, name, url.?, media.?, extra, available) <> ((FileMeta.fromRow _).tupled, FileMeta.toRow)
  }

  private[model] val files = TableQuery[Files]
}
