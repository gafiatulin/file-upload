package com.github.gafiatulin.actor

import java.nio.channels.SeekableByteChannel
import java.nio.file.{Files, Path, StandardOpenOption}

import akka.Done
import akka.actor.{ActorLogging, Props}
import akka.persistence.{DeleteMessagesSuccess, PersistentActor, RecoveryCompleted}
import akka.stream.actor.{ActorSubscriber, WatermarkRequestStrategy}
import akka.util.ByteString

import scala.concurrent.Promise
import scala.util.Try

/**
  * Created by victor on 18/07/16.
  */

class FileUploader(hash: String, dir: Path) extends ActorSubscriber with PersistentActor with ActorLogging{
  import akka.stream.actor.ActorSubscriberMessage._
  private var fileName = ""
  private var mediaType = ""
  private var fileId = 0L
  private var size = 0L
  private var seqNumber = 0L
  private var byteChanel: Option[SeekableByteChannel] = None
  private val promise = Promise[Long]()

  override def persistenceId: String = hash

  override val requestStrategy = new WatermarkRequestStrategy(highWatermark = FileUploader.highWatermark)

  def initByteChannel(fileName: String, offset: Long): Unit = {
    log.debug("Initializing ByteChannel")
    byteChanel = Try(Files.newByteChannel(dir.resolve(fileName), StandardOpenOption.WRITE, StandardOpenOption.CREATE)).toOption
    byteChanel.foreach(_.position(offset))
  }

  override def receiveCommand: Receive = {
    case Terminate =>
      log.debug("Terminating. State: seqNumber = {}, size = {} ", seqNumber, size)
      sender ! Done
      context.stop(self)
    case CleanUp =>
      log.debug("Deleting all messages (up to {})", seqNumber)
      deleteMessages(seqNumber)
      sender ! Done
    case SetNameIdAndMediaType(name, id, mType) =>
      persist[NameIdAndMediaTypeChanged](NameIdAndMediaTypeChanged(name, id, mType)){ evt =>
        fileName = evt.name
        mediaType = mType
        fileId = id
        log.debug("Setting name to {}, id to {} and media to {}", fileName, fileId, mediaType)
        initByteChannel(fileName, size)
        seqNumber += 1
        sender ! Done
      }
    case OnNext(bs: ByteString) =>
      log.debug("{}", bs.length)
      val written = byteChanel.flatMap(x => Try(x.write(bs.toByteBuffer)).toOption).getOrElse(0)
      log.debug("Written {} to {}. ByteChannel position: {}", written, fileName, byteChanel.map(_.position))
      persist[ChunkWritten](ChunkWritten(written.toLong)){ evt =>
        seqNumber += 1
        size += evt.size
      }
    case OnNext(x) =>
      log.debug("Received {}", x)
    case OnError(t) =>
      log.debug("Received upstream error {}", t)
      promise.failure(t)
      self ! Terminate
    case OnComplete =>
      log.debug("Stream completed. Completing the promise with success")
      promise.success(fileId)
      self ! CleanUp
      ()
    case GetCompletion =>
      log.debug("Returning promise")
      sender ! promise
    case DeleteMessagesSuccess(_) =>
      log.debug("Successfully deleted all messages")
      self ! Terminate
  }

  override def receiveRecover: Receive = {
    case RecoveryCompleted =>
      log.debug("RecoveryCompleted")
      initByteChannel(fileName, size)
    case NameIdAndMediaTypeChanged(name, id, mType) =>
      seqNumber += 1
      fileName = name
      mediaType = mType
      fileId = id
    case ChunkWritten(chunkSize) =>
      seqNumber += 1
      size = size + chunkSize
  }
}

object FileUploader{
  val highWatermark = 50
  def props(hash: String, directory: Path): Props = Props(classOf[FileUploader], hash, directory)
}
