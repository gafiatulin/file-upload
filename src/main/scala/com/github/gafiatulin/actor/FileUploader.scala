package com.github.gafiatulin.actor

import java.nio.channels.SeekableByteChannel
import java.nio.file.{Files, Path, StandardOpenOption}

import akka.Done
import akka.actor.{ActorLogging, Props}
import akka.persistence.{DeleteMessagesSuccess, PersistentActor, RecoveryCompleted}
import akka.stream.actor.{ActorSubscriber, MaxInFlightRequestStrategy}
import akka.util.ByteString

import scala.concurrent.Promise
import scala.util.Try

/**
  * Created by victor on 18/07/16.
  */

class FileUploader(hash: String, dir: Path) extends PersistentActor with ActorSubscriber with ActorLogging{
  import akka.stream.actor.ActorSubscriberMessage._
  private var inFlight = 0
  private var fileName = ""
  private var fileId = 0L
  private var size = 0L
  private var seqNumber = 0L
  private var byteChanel: Option[SeekableByteChannel] = None
  private val promise = Promise[Long]()

  override def persistenceId = hash

  override protected def requestStrategy = new MaxInFlightRequestStrategy(10) {
    override def inFlightInternally = inFlight
  }

  def initByteChannel(fileName: String, offset: Long): Unit = {
    log.debug("Initializing ByteChannel")
    byteChanel = Try(Files.newByteChannel(dir.resolve(fileName), StandardOpenOption.WRITE, StandardOpenOption.CREATE)).toOption
    log.debug("Setting ByteChannel offset = {}", offset)
    byteChanel.foreach(_.position(offset))

  }

  override def receiveCommand = {
    case Terminate =>
      log.debug("Terminating. State: seqNumber = {}, size = {} ", seqNumber, size)
      sender ! Done
      context.stop(self)
    case CleanUp =>
      log.debug("Deleting all messages (up to {})", seqNumber)
      deleteMessages(seqNumber)
      sender ! Done
    case SetNameAndId(name, id) =>
      inFlight += 1
      persist[NameAndIdChanged](NameAndIdChanged(name, id)){ evt =>
        fileName = evt.name
        fileId = id
        log.debug("Setting name to {} and id to {}", fileName, fileId)
        initByteChannel(fileName, size)
        seqNumber += 1
        inFlight -= 1
        sender ! Done
      }
    case OnNext(bs: ByteString) =>
      inFlight += 1
      val written = byteChanel.flatMap(x => Try(x.write(bs.toByteBuffer)).toOption).getOrElse(0)
      log.debug("Written {} to {}", written, fileName)
      persist[ChunkWritten](ChunkWritten(written.toLong)){ evt =>
        seqNumber += 1
        size += evt.size
      }
    case OnError(t) =>
      log.debug("Received upstream error {}", t)
      promise.failure(t)
      self ! Terminate
    case OnComplete =>
      log.debug("Stream completed. Completing the promise with success")
      promise.success(fileId)
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
    case NameAndIdChanged(name, id) =>
      seqNumber += 1
      fileName = name
      fileId = id
    case ChunkWritten(chunkSize) =>
      seqNumber += 1
      size = size + chunkSize
  }
}

object FileUploader{
  def props(hash: String, directory: Path): Props = Props(classOf[FileUploader], hash, directory)
}