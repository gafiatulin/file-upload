package com.github.gafiatulin.actor

/**
  * Created by victor on 18/07/16.
  */

sealed trait Evt
case class NameAndIdChanged(name: String, id: Long) extends Evt
case class ChunkWritten(size: Long) extends Evt