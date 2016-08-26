package com.github.gafiatulin.routes

import akka.event.Logging
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.{RangeUnits, `Content-Range`}
import akka.http.scaladsl.model.{ContentRange, HttpResponse, RequestEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.github.gafiatulin.file.LocalFileStorageAdapter
import com.github.gafiatulin.model.FileMetaUtils

import scala.concurrent.Future

/**
  * Created by victor on 18/07/16.
  */
trait FileUpload extends FileMetaUtils with RoutingUtils {

  private def handleEmptyEntity(adapter: LocalFileStorageAdapter, token: String) = headerValueByType[`Content-Range`](){
    case `Content-Range`(RangeUnits.Bytes, ContentRange.Unsatisfiable(_)) =>
      val resp = adapter.uploaded(token)
        .flatMap{
          case Some((last, _)) =>
            val headers = if (last == 0) {List()} else {List(`Content-Range`(RangeUnits.Bytes, ContentRange.Default(0, last -1, None)))}
            Future.successful(HttpResponse(headers = headers))
          case None => Marshal(StatusCodes.BadRequest -> "Specified uploadToken is incorrect or already completed").to[HttpResponse]
        }
        .recoverWith(recoverAfterException)
      complete(resp)
    case _ =>
      val resp = Marshal(StatusCodes.BadRequest -> "Inconsistent Content-Range header value").to[HttpResponse]
      complete(resp)
  }

  private def handleEntity(adapter: LocalFileStorageAdapter, token: String, ent: RequestEntity) = optionalHeaderValueByType[`Content-Range`](){
    case Some(`Content-Range`(RangeUnits.Bytes, ContentRange.Default(first, _, _))) =>
      val resp = adapter.upload(token, ent.dataBytes, ent.contentType.mediaType, first)
        .flatMap(completeFileUpload)
        .flatMap(_ => Marshal(StatusCodes.Created).to[HttpResponse])
        .recoverWith(recoverAfterException)
      complete(resp)
    case Some(contentRange) =>
      val resp = Marshal(StatusCodes.BadRequest -> "Malformed Content-Range").to[HttpResponse]
      complete(resp)
    case None =>
      val resp = adapter.upload(token, ent.dataBytes, ent.contentType.mediaType)
        .flatMap(completeFileUpload)
        .flatMap(_ => Marshal(StatusCodes.Created).to[HttpResponse])
        .recoverWith(recoverAfterException)
      complete(resp)
  }

  private def handleUpload(adapter: LocalFileStorageAdapter, token: String): Route = extractRequestEntity{
    case ent: RequestEntity if ent.isKnownEmpty => handleEmptyEntity(adapter, token)
    case ent: RequestEntity if safeMediaRanges.exists(_.matches(ent.contentType.mediaType)) => handleEntity(adapter, token, ent)
    case ent: RequestEntity =>
      val resp = Marshal((
        StatusCodes.UnsupportedMediaType,
        s"MediaType ${ent.contentType.mediaType} is not supported. Supported MediaTypes: ${safeMediaRanges.mkString("[", ",", "]")}"
      )).to[HttpResponse]
      complete(resp)
  }

  def fileUploadRoute: Route = (put & pathPrefix("upload") & parameter("upload_token")){ uploadToken =>
    logRequestResult("upload", Logging.DebugLevel){
      fsAdapter match {
        case adapter: LocalFileStorageAdapter => handleUpload(adapter, uploadToken)
        case _ => complete(Marshal(StatusCodes.NotFound).to[HttpResponse])
      }
    }
  }
}
