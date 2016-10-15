package rings

import akka._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet


sealed trait LockServiceAPI
// Actors send the following commands to LockServers
case class Acquire(senderNodeID: BigInt, loclId: String) extends LockServiceAPI
// case class Release(senderNodeID: BigInt, lockId: String) extends LockServiceAPI

// LockServer send the following command to Actors
// case class ReleaseLock(senderNodeID: BigInt, lockId: String) extends LockServiceAPI

class LockCell(var lockId: String, var lockHolders: HashSet[String])

class LockServer (storeServers: Seq[ActorRef]) extends Actor {
  val generator = new scala.util.Random
  val cellstore = new KVClient(storeServers)

  def receive() = {
    case Acquire(lockId: String, nodeId: BigInt) =>
      acquire(lockId,myNodeID)
  }

  def acquire(lockId: String, myNodeID: BigInt) {
    println(s"Acquire called by $myNodeID")
  }
}
object LockServer {
  def props(storeServers: Seq[ActorRef]): Props = {
    Props(classOf[LockServer], storeServers)
  }
}