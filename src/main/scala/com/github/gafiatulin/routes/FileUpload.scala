package com.github.gafiatulin.routes

import akka.event.Logging
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.{ByteRange, Range, RangeUnits, `Content-Range`}
import akka.http.scaladsl.model.{ContentRange, HttpResponse, RequestEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.github.gafiatulin.file.LocalFileStorageAdapter
import com.github.gafiatulin.model.FileMetaUtils

/**
  * Created by victor on 18/07/16.
  */
trait FileUpload extends FileMetaUtils with RoutingUtils {
  private def handleUpload(adapter: LocalFileStorageAdapter, token: String): Route = extractRequestEntity{
    case ent: RequestEntity if ent.isKnownEmpty || safeMediaRanges.exists(_.matches(ent.contentType.mediaType)) =>
      optionalHeaderValueByType[`Content-Range`](){
        case Some(`Content-Range`(RangeUnits.Bytes, ContentRange.Unsatisfiable(fileSize))) =>
          val resp = adapter.uploaded(token)
            .map(last => HttpResponse(headers = List(Range(ByteRange(0, last)))))
            .recoverWith(recoverAfterException)
          complete(resp)
        case Some(`Content-Range`(RangeUnits.Bytes, ContentRange.Default(first, _, _))) =>
          val resp = adapter.upload(token, ent.dataBytes, first)
          .flatMap(completeFileUpload)
          .flatMap(_ => Marshal(StatusCodes.Created).to[HttpResponse])
          .recoverWith(recoverAfterException)
          complete(resp)
        case Some(contentRange) =>
          val resp = Marshal(StatusCodes.BadRequest -> "Malformed Content-Range").to[HttpResponse]
          complete(resp)
        case None =>
          val resp = adapter.upload(token, ent.dataBytes)
            .flatMap(completeFileUpload)
            .flatMap(_ => Marshal(StatusCodes.Created).to[HttpResponse])
            .recoverWith(recoverAfterException)
          complete(resp)
      }
    case ent: RequestEntity =>
      val str = s"MediaType ${ent.contentType.mediaType} is not supported. Supported MediaTypes: ${safeMediaRanges.mkString("[", ",", "]")}"
      val resp = Marshal(StatusCodes.UnsupportedMediaType -> str).to[HttpResponse]
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
