package com.github.gafiatulin.file

import java.nio.file.{Files, Paths}
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.pattern.ask
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import com.github.gafiatulin.actor._
import com.github.gafiatulin.util.ErrorResponse

import scala.concurrent.{ExecutionContextExecutor, Future, Promise}

/**
  * Created by victor on 18/07/16.
  */

final case class LocalFileStorageAdapter(
  actorSystem: ActorSystem,
  executor: ExecutionContextExecutor,
  fileStorageConfig: com.typesafe.config.Config) extends FileStorageAdapter {
  private val logger = actorSystem.log
  private implicit val materializer = ActorMaterializer()(actorSystem)
  private val hashLength = fileStorageConfig.getInt("hashLength")
  private val filesDirectory = Paths.get(fileStorageConfig.getString("filesLocation")).toAbsolutePath.normalize
  private val uploadPrefix = fileStorageConfig.getString("uploadUrlPrefix")
  private implicit val timeout: Timeout = Timeout(fileStorageConfig.getLong("askTimeOut.length"), TimeUnit.valueOf(fileStorageConfig.getString("askTimeOut.unit")))
  private implicit val ec: ExecutionContextExecutor = executor
  if (!Files.exists(filesDirectory)) Files.createDirectories(filesDirectory)
  if(Files.isWritable(filesDirectory) && Files.isDirectory(filesDirectory)) {
    logger.info("Using {} for storing assets", filesDirectory)
  } else {
    throw new RuntimeException(s"Doesn't have write permissions for $filesDirectory")
  }

  private def deleteFileByName(name: String): Future[Unit] = Future{
    Files.delete(filesDirectory.resolve(name))
  }

  def getFileUploadUrl(id: Long, name: String): Future[Uri] = {
    val h = hash(id, hashLength)
    val actor = actorSystem.actorOf(FileUploader.props(h, filesDirectory))
    for{
      _ <- actor.ask(SetNameAndId(name, id))
      _ <- actor.ask(Terminate)
    } yield Uri(uploadPrefix + h)
  }

  def deleteFileBy(fileName: String, id: Long, completed: Boolean = true): Future[Unit] = if(completed){
    deleteFileByName(fileName)
  } else {
    val actor = actorSystem.actorOf(FileUploader.props(hash(id, hashLength), filesDirectory))
    actor.ask(CleanUp).flatMap(_ => deleteFileByName(fileName))
  }

  def uploaded(uToken: String): Future[Long] = {
    PersistenceQuery(actorSystem)
      .readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)
      .eventsByPersistenceId(uToken).runFold(0L){
        case (acc, EventEnvelope(_, _, _, ChunkWritten(chunkSize))) => acc + chunkSize
        case (acc, _) => acc
      }
  }

  def upload(uToken: String, source: Source[ByteString, Any], offset: Long = 0): Future[Long] = {
    uploaded(uToken).flatMap{
      case done if done == offset =>
        val actor = source.runWith(Sink.actorSubscriber(FileUploader.props(uToken, filesDirectory)))
        actor.ask(GetCompletion).mapTo[Promise[Long]].flatMap(_.future)
      case done => throw ErrorResponse(s"Wrong range. Should start at $done", StatusCodes.BadRequest)
    }
  }
}