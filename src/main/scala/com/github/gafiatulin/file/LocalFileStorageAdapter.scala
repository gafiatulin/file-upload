package com.github.gafiatulin.file

import java.nio.file.{Files, Paths}
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
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
  private implicit val timeout: Timeout = Timeout(
    fileStorageConfig.getLong("askTimeOut.length"),
    TimeUnit.valueOf(fileStorageConfig.getString("askTimeOut.unit"))
  )
  private implicit val ec: ExecutionContextExecutor = executor
  if (!Files.exists(filesDirectory)) Files.createDirectories(filesDirectory)
  if(Files.isWritable(filesDirectory) && Files.isDirectory(filesDirectory)) {
    logger.info("Using {} for storing files", filesDirectory)
  } else {
    throw new RuntimeException(s"Don't have write permissions for $filesDirectory")
  }

  private def deleteFileByName(name: String): Future[Unit] = Future{
    Files.delete(filesDirectory.resolve(name))
  }

  def getFileUploadUrl(id: Long, name: String, media: Option[MediaType]): Future[Uri] = {
    val h = hash(id, hashLength)
    val actor = actorSystem.actorOf(FileUploader.props(h, filesDirectory))
    for{
      _ <- actor.ask(SetNameIdAndMediaType(name, id, media.toString))
      _ <- actor.ask(Terminate)
    } yield Uri(uploadPrefix + h)
  }

  def deleteFileBy(fileName: String, id: Long, completed: Boolean = true): Future[Unit] = if(completed){
    deleteFileByName(fileName)
  } else {
    val actor = actorSystem.actorOf(FileUploader.props(hash(id, hashLength), filesDirectory))
    actor.ask(CleanUp).flatMap(_ => deleteFileByName(fileName))
  }

  def uploaded(uToken: String): Future[Option[(Long, Option[MediaType])]] = {
    PersistenceQuery(actorSystem)
      .readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)
      .currentEventsByPersistenceId(uToken).runFold[Option[(Long, Option[MediaType])]](None){
        case (None, EventEnvelope(_, _, _, NameIdAndMediaTypeChanged(_, _, media))) =>
          val (m, s) = media.splitAt(media.indexOf('/'))
          Some(0L -> MediaTypes.getForKey(m -> s.tail))
        case (None, EventEnvelope(_, _, _, ChunkWritten(chunkSize))) => Some(chunkSize -> None)
        case (acc, EventEnvelope(_, _, _, ChunkWritten(chunkSize))) => acc.map(t => t._1 + chunkSize -> t._2)
        case (acc, EventEnvelope(_, _, _, NameIdAndMediaTypeChanged(_, _, media))) =>
          val (m, s) = media.splitAt(media.indexOf('/'))
          acc.map(t => t._1 -> t._2.orElse(MediaTypes.getForKey(m -> s.tail)))
        case (acc, _) => acc
      }
  }

  def upload(uToken: String, source: Source[ByteString, Any], mType: MediaType, offset: Long = 0): Future[Long] = {
    uploaded(uToken).flatMap{
      case Some((done, media)) if done == offset && media.forall(_ == mType) =>
        val actor = source.runWith(Sink.actorSubscriber(FileUploader.props(uToken, filesDirectory)))
        actor.ask(GetCompletion).mapTo[Promise[Long]].flatMap(_.future)
      case Some((done, media)) if done == offset => throw ErrorResponse(s"Wrong MediaType. Expected: ${media.get}", StatusCodes.BadRequest)
      case Some((done, _)) => throw ErrorResponse(s"Wrong range. Should start at $done", StatusCodes.BadRequest)
      case None => throw ErrorResponse("Specified uploadToken is incorrect or already completed", StatusCodes.BadRequest)
    }
  }
}
