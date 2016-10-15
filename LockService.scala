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
case class Acquire(lockId: String, senderNodeID: BigInt) extends LockServiceAPI
// case class Release(senderNodeID: BigInt, lockId: String) extends LockServiceAPI

// LockServer send the following command to Actors
// case class ReleaseLock(senderNodeID: BigInt, lockId: String) extends LockServiceAPI

class LockCell(var lockId: String, var lockHolders: BigInt) // need to change this so that list of nodes can be stored against a lock 

class LockServer (storeServers: Seq[ActorRef]) extends Actor {
  val generator = new scala.util.Random
  val cellstore = new KVClient(storeServers)
  private val master_lock = new Object()
  private var count: BigInt = 0

  def receive() = {
    case Command() =>
      command
    case Acquire(a: String, b: BigInt) =>
      acquire(a,b)
    // case Acquire(lockId, nodeId) =>
    //   acquire(lockId,myNodeID)
  }
  def command() = {
    println(s"3 command")
  }
  def acquire(lockId: String, myNodeID: BigInt) = {
    // count = count + 1
    // println(s"3 $lockId Acquire called by $myNodeID $count")
    checkMaster(myNodeID)
  }
  def checkMaster(id: BigInt): Unit = master_lock.synchronized  {
    val cell = directRead(findHash("MASTER"))
    if (cell.isEmpty) {
      makeMaster(id)
    } else {
      var lc = cell.get
      var master_id = lc.lockHolders
      println(s"******* $master_id is already master")
    }
  }
  
  def makeMaster(id: BigInt): Unit = master_lock.synchronized {
    println(s"******* $id is master")
    var lc = new LockCell("MASTER", id) // hardcoded for master since this function is only called for master selection 
    directWrite(findHash("MASTER"), lc)
  }

  // to convert lock name string to bigint hash
  import java.security.MessageDigest
  def findHash(lc: String): BigInt = {
    val md: MessageDigest = MessageDigest.getInstance("MD5")
    val digest: Array[Byte] = md.digest(lc.getBytes)
    BigInt(1, digest)
  }

  // Code to access KVClient
  private def directRead(key: BigInt): Option[LockCell] = {
    val result = cellstore.directRead(key)
    if (result.isEmpty) None else
      Some(result.get.asInstanceOf[LockCell])
  }

  private def directWrite(key: BigInt, value: LockCell): Option[LockCell] = {
    val result = cellstore.directWrite(key, value)
    if (result.isEmpty) None else
      Some(result.get.asInstanceOf[LockCell])
  }

  // def method1(): Unit = lock.synchronized {
  //   println("method1")
  // }
}
object LockServer {
  def props(storeServers: Seq[ActorRef]): Props = {
    Props(classOf[LockServer], storeServers)
  }
}