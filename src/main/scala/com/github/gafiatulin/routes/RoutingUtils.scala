package com.github.gafiatulin.routes

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import com.github.gafiatulin.util.{ErrorResponse, JsonSupport}

import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * Created by victor on 18/07/16.
  */
trait RoutingUtils extends JsonSupport {
  implicit val ec: ExecutionContextExecutor
  val recoverAfterException: PartialFunction[Throwable, Future[HttpResponse]] = {
    case e: ErrorResponse =>
      Marshal(e.statusCode -> e.message).to[HttpResponse]
    case t: Throwable => Marshal(StatusCodes.InternalServerError -> t.getMessage).to[HttpResponse]
  }
}
