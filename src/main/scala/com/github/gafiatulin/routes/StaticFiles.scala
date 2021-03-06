package com.github.gafiatulin.routes

import akka.event.Logging
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.github.gafiatulin.util.Config

/**
  * Created by victor on 18/07/16.
  */
trait StaticFiles extends Config {
  def staticRoutes: Route = (get & pathPrefix("static")){
    logRequestResult("static", Logging.DebugLevel){
      pathEndOrSingleSlash{
        redirect(Uri.from(host = httpInterface, port = httpPort, path = "/files"), StatusCodes.PermanentRedirect)
      } ~ getFromBrowseableDirectory(staticFilesDirectory.toString)
    }
  }
}
