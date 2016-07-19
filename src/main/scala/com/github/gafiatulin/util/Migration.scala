package com.github.gafiatulin.util

import java.util.concurrent.TimeUnit

import akka.event.LoggingAdapter
import org.flywaydb.core.Flyway
import slick.jdbc.JdbcProfile

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Created by victor on 18/07/16.
  */
trait Migration extends Config{
  private val flyway = new Flyway()
  flyway.setDataSource(databaseUrl, databaseUser, databasePassword)

  val driver: JdbcProfile
  val log: LoggingAdapter

  //Dirty hack
  private def createIfNotExists()(implicit ec: ExecutionContext) = {
    val dbURL = databaseUrl.take(databaseUrl.indexOfSlice(databaseName)) + dbSpecificDefaultDB
    import driver.api._
    val db = Database.forURL(dbURL, user = databaseUser, password = databasePassword)
    val f = db.run(sql"#$dbSpecificExistenceCheckQuery".as[String].headOption.asTry.transactionally).flatMap{
      case Success(Some(_)) =>
        log.info("Database already exists")
        Future.successful(Success(()))
      case Success(None) =>
        log.info("Database does not exist. Creating...")
        db.run(sqlu"#$dbSpecificDatabaseCreationQuery".asTry)
      case Failure(t) =>
        log.error(t, "Existence check failed")
        Future.successful(Failure(t))
    }
    val timeout = 5L
    Await.result(f, Duration(timeout, TimeUnit.SECONDS)) match {
      case Success(_) => Some(())
      case _ => None
    }
  }
  //

  private val migration = () => {
    flyway.setBaselineOnMigrate(true)
    flyway.migrate()
    !flyway.info.current.getState.isFailed
  }
  private val reload = () => {
    flyway.clean()
    flyway.migrate()
    !flyway.info.current.getState.isFailed
  }

  private def init(f: () => Boolean)(implicit ec: ExecutionContext): Boolean = {
    if(createIfNotExist) {
      createIfNotExists().exists(_ => f())
    } else {
      f()
    }
  }

  def migrate()(implicit ec: ExecutionContext): Boolean = {
    init(migration)
  }
  def reloadSchema()(implicit ec: ExecutionContext): Boolean = {
    init(reload)
  }
}
