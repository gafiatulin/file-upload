package com.github.gafiatulin.util

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{MediaRanges, Uri}
import com.github.gafiatulin.model.FileMeta
import spray.json.{DefaultJsonProtocol, DeserializationException, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

import scala.util.Try

/**
  * Created by victor on 18/07/16.
  */
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol{
  implicit val fileMetaFormat = new RootJsonFormat[FileMeta]{
    override def read(json: JsValue): FileMeta = {
      val fields = json.asJsObject.fields
      (for{
        id <- Try(fields.get("id").map(_.convertTo[Long]))
        name <- Try(fields("fileName").convertTo[String])
        url <- Try(fields.get("url").map(x => Uri(x.convertTo[String])))
        media <- Try(fields.get("media").flatMap(x => MediaRanges.getForKey(x.convertTo[String])))
      } yield FileMeta(id, name, url, media))
        .getOrElse(throw DeserializationException("FileMeta expected"))
    }

    override def write(obj: FileMeta): JsValue = JsObject{
      Map("fileName" -> JsString(obj.name)) ++
      Seq(
        obj.id.map("id" -> JsNumber(_)),
        obj.url.map(x => "url" -> JsString(x.toString)),
        obj.media.map(x => "media" -> JsString(x.mainType))
      ).flatten
    }
  }
}
