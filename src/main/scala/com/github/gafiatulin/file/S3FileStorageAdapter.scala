package com.github.gafiatulin.file

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri

import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * Created by victor on 18/07/16.
  */

final case class S3FileStorageAdapter(
  actorSystem: ActorSystem,
  executor: ExecutionContextExecutor,
  fileStorageConfig: com.typesafe.config.Config) extends FileStorageAdapter{
  def getFileUploadUrl(id: Long, name: String): Future[Uri] = throw new NotImplementedError
  def deleteFileBy(fileName: String, id: Long, completed: Boolean = true): Future[Unit] = throw new NotImplementedError
}
