package com.github.gafiatulin.util

import akka.http.scaladsl.model.StatusCode

/**
  * Created by victor on 18/07/16.
  */

case class ErrorResponse(message: String, statusCode: StatusCode = akka.http.scaladsl.model.StatusCodes.InternalServerError) extends Exception