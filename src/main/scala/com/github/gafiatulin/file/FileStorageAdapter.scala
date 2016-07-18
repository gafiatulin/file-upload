package com.github.gafiatulin.file

import akka.http.scaladsl.model.Uri

import scala.concurrent.Future
import scala.util.Random

/**
  * Created by victor on 18/07/16.
  */
trait FileStorageAdapter {
  def getFileUploadUrl(id: Long, name: String): Future[Uri]
  def deleteFileBy(fileName: String, id: Long, completed: Boolean): Future[Unit]
  protected def hash(id: Long, length: Int): String = {
    new Random(id).alphanumeric.take(length).mkString
  }
}
