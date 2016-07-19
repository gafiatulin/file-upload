package com.github.gafiatulin.actor

/**
  * Created by victor on 18/07/16.
  */

sealed trait Evt
case class NameIdAndMediaTypeChanged(name: String, id: Long, mType: String) extends Evt
case class ChunkWritten(size: Long) extends Evt
