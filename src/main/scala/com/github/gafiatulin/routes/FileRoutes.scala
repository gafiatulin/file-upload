package com.github.gafiatulin.routes

import akka.event.Logging
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.github.gafiatulin.model.{FileMeta, FileMetaUtils}

/**
  * Created by victor on 18/07/16.
  */
trait FileRoutes extends FileMetaUtils with RoutingUtils{
  private def fileCreation: Route = (post & entity(as[FileMeta])){fm =>
    val resp = persistFileMeta(fm.copy(url = None, available = false))
      .flatMap(id => fsAdapter.getFileUploadUrl(id, fm.name))
      .map(uploadUrl => HttpResponse().withHeaders(Location(uploadUrl)))
      .recoverWith(recoverAfterException)
    complete(resp)
  }
  private def filesQuery: Route = (get & parameterSeq){ params =>
    val resp = queryFileMeta(params)
      .flatMap(Marshal(_).to[HttpResponse])
      .recoverWith(recoverAfterException)
    complete(resp)
  }
  private def fileDeletion: Route = (delete & pathPrefix(LongNumber)){id =>
    val resp = deleteExisting(id)
      .flatMap(_ => Marshal(StatusCodes.Accepted).to[HttpResponse])
      .recoverWith(recoverAfterException)
    complete(resp)
  }

  def fileRoutes: Route  = pathPrefix("files"){
    logRequestResult("files", Logging.DebugLevel){
      fileCreation ~ filesQuery ~ fileDeletion
    }
  }
}
