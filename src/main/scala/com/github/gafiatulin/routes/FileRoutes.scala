package com.github.gafiatulin.routes

import akka.event.Logging
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.github.gafiatulin.model.{FileMeta, FileMetaUtils}
import spray.json.JsString

/**
  * Created by victor on 18/07/16.
  */
trait FileRoutes extends FileMetaUtils with RoutingUtils{
  private def fileCreation: Route = (post & entity(as[FileMeta])){fm =>
    val meta = (fm.url match {
      case Some(_) => fm
      case None => fm.copy(url = Some(Uri(staticFilesUrlPrefix + fm.name)))
    }).copy(available = false)
    val resp = persistFileMeta(meta)
      .flatMap(id => fsAdapter.getFileUploadUrl(id, fm.name, fm.media))
      .map(uploadUrl => HttpResponse().withHeaders(Location(uploadUrl)))
      .recoverWith(recoverAfterException)
    complete(resp)
  }
  private def filesQuery: Route = (get & pathEndOrSingleSlash & parameterSeq){ params =>
    implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()
    complete(queryFileMeta(params))
  }
  private def fileDeletion: Route = (delete & pathPrefix(LongNumber)){id =>
    val resp = deleteExisting(id)
      .flatMap(_ => Marshal(StatusCodes.Accepted).to[HttpResponse])
      .recoverWith(recoverAfterException)
    complete(resp)
  }

  private def currentlyUploading: Route = (get & path("uploading")){
    implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()
    val resp = allUnavailable.map(meta => uploadHashLength.map{l =>
      val url = JsString(uploadPrefix + fsAdapter.hash(meta.id.get, l))
      meta.copy(extra = meta.extra.asJsObject.copy(meta.extra.asJsObject.fields.updated("upload_url", url)))
    }.getOrElse(meta))
    complete(resp)
  }

  def fileRoutes: Route  = pathPrefix("files"){
    logRequestResult("files", Logging.DebugLevel){
      fileCreation ~ currentlyUploading ~ filesQuery ~ fileDeletion
    }
  }
}
