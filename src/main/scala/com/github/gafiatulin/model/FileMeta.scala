package com.github.gafiatulin.model

import akka.http.scaladsl.model.{MediaRange, MediaRanges, Uri}
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape

/**
  * Created by victor on 18/07/16.
  */
case class FileMeta(id: Option[Long], name: String, url: Option[Uri], media: Option[MediaRange], available: Boolean = false)

object FileMeta{
  def toRow(obj: FileMeta): Option[(Option[Long], String, Option[String], Option[String], Boolean)] =
    Some((obj.id, obj.name, obj.url.map(_.toString), obj.media.map(_.mainType), obj.available))
  def fromRow(fId: Option[Long], fName: String, fUrl: Option[String], fMedia: Option[String],  fAvailability: Boolean): FileMeta =
    FileMeta(fId, fName, fUrl.map(Uri(_)), fMedia.flatMap(MediaRanges.getForKey), fAvailability)
}

trait FilesTable{
  val driver: JdbcProfile
  import driver.api._
  class Files(tag: Tag) extends Table[FileMeta](tag, "files"){
    def id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    def name: Rep[String] = column[String]("name")
    def url: Rep[String] = column[String]("url")
    def media: Rep[String] = column[String]("media")
    def available: Rep[Boolean] = column[Boolean]("available")
    def * : ProvenShape[FileMeta] = (id.?, name, url.?, media.?, available) <> ((FileMeta.fromRow _).tupled, FileMeta.toRow)
  }

  private [model] val files = TableQuery[Files]
}
