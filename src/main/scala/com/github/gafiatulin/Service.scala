package com.github.gafiatulin

import akka.http.scaladsl.server.{Route, RouteConcatenation}
import com.github.gafiatulin.routes.{FileRoutes, FileUpload, StaticFiles}

/**
  * Created by victor on 18/07/16.
  */
trait Service extends FileRoutes with FileUpload with StaticFiles with RouteConcatenation {
  def routes: Route = fileRoutes ~ fileUploadRoute ~ staticRoutes
}
