package com.github.gafiatulin.util

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{MediaRange, MediaRanges}
import com.github.gafiatulin.file.FileStorageAdapter
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success, Try}

/**
  * Created by victor on 18/07/16.
  */
trait Config {
  import collection.JavaConversions._
  val system: ActorSystem
  val ec: ExecutionContextExecutor
  private val config = ConfigFactory.load()
  private val httpConfig = config.getConfig("http")
  private val databaseConfig = config.getConfig("database")

  val httpInterface = httpConfig.getString("interface")
  val httpPort = httpConfig.getInt("port")

  val databaseUrl = databaseConfig.getString("url")
  val databaseUser = databaseConfig.getString("user")
  val databasePassword = databaseConfig.getString("password")

  //Dirty hack
  val databaseName = databaseConfig.getString("name")
  val createIfNotExist = Try(databaseConfig.getBoolean("createIfNotExist")).getOrElse(false)
  val dbSpecificDefaultDB = Try(databaseConfig.getString("dbSpecificDefaultDB")).getOrElse("")
  val dbSpecificDatabaseCreationQuery = Try(databaseConfig.getString("dbSpecificDatabaseCreationQuery")).getOrElse("")
  val dbSpecificExistenceCheckQuery = Try(databaseConfig.getString("dbSpecificExistenceCheckQuery")).getOrElse("")
  //

  val safeMediaRanges: Seq[MediaRange] = config.getStringList("safeMedia").flatMap(s => MediaRanges.getForKey(s))

  val fsConfig = config.getConfig("fsConfig")
  lazy val fsAdapter: FileStorageAdapter = Try{
    Class
      .forName(fsConfig.getString("adapter"))
      .asSubclass(classOf[FileStorageAdapter])
      .getDeclaredConstructor(classOf[ActorSystem], classOf[ExecutionContextExecutor], classOf[com.typesafe.config.Config])
      .newInstance(system, ec, fsConfig)
  } match {
    case Success(adapter) => adapter
    case Failure(e) => throw new RuntimeException("Failed to initialize FileStorageAdapter", e)
  }

  val staticFilesUrlPrefix = config.getString("staticFilesUrlPrefix")
  val staticFilesDirectory = Paths.get(fsConfig.getString("filesLocation")).toAbsolutePath.normalize

  val uploadHashLength: Option[Int] = Try(fsConfig.getInt("hashLength")).toOption
  val uploadPrefix: String = fsConfig.getString("uploadUrlPrefix")
}
