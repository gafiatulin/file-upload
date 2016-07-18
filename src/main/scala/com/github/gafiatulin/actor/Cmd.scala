package com.github.gafiatulin.actor

/**
  * Created by victor on 18/07/16.
  */

sealed trait Cmd
case class SetNameAndId(name: String, id: Long) extends Cmd
case object Terminate extends Cmd
case object GetCompletion extends Cmd
case object CleanUp extends Cmd