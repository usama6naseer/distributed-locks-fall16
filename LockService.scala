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
case class Release(lockId: String, senderNodeID: BigInt) extends LockServiceAPI

// LockServer send the following command to Actors
// case class ReleaseLock(senderNodeID: BigInt, lockId: String) extends LockServiceAPI

class LockCell(var lockId: String, var lockHolders: BigInt) // need to change this so that list of nodes can be stored against a lock 

class LockServer (storeServers: Seq[ActorRef], clientServers: Seq[ActorRef]) extends Actor {
  val generator = new scala.util.Random
  val cellstore = new KVClient(storeServers)
  private val master_lock = new Object()
  private var count: BigInt = 0

  def receive() = {
    case Command() =>
      command
    case Acquire(a: String, b: BigInt) =>
      acquire(a,b)
    case Release(a: String, b: BigInt) =>
      release(a,b)
  }
  def command() = {
    println(s"3 command")
  }
  def acquire(lockId: String, myNodeID: BigInt) = {
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
      if (master_id == -1) {
        makeMaster(id)
      }
      else {
        // println(s"******* $master_id is already master")
        // inform client of the master
        clientServers(id.toInt) ! InformClientMasterSame(master_id)
      }
    }
  }
  def makeMaster(id: BigInt): Unit = master_lock.synchronized {
    var lc = new LockCell("MASTER", id) // hardcoded for master since this function is only called for master selection 
    directWrite(findHash("MASTER"), lc)
    // println(s"******* $id is master")
    // inform client of the master
    clientServers(id.toInt) ! InformClientMasterChange(id)
  }
  def release(lockId: String, myNodeID: BigInt): Unit = master_lock.synchronized {
    // three cases for locks i-2 master, read write
    if (lockId == "MASTER") {
      var cell = directRead(findHash("MASTER"))
      if (cell.isEmpty) {
        clientServers(myNodeID.toInt) ! InformClientMasterRelease(-1) // return -1 to client since it cannot release a lock it doesn't have
      }
      else {
        var gc = cell.get
        var m_id = gc.lockHolders
        if (m_id != myNodeID) {
          clientServers(myNodeID.toInt) ! InformClientMasterRelease(-1) // return -1 to client since it cannot release a lock it doesn't have
        }
        else {
          var lc = new LockCell("MASTER", -1) // -1 represents master lock released case 
          directWrite(findHash("MASTER"), lc)
          // inform client of the master
          clientServers(myNodeID.toInt) ! InformClientMasterRelease(myNodeID)        
        }        
      }
    }
    else if (lockId == "READ") {

    }
    else if (lockId == "WRITE") {

    }
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
  def props(storeServers: Seq[ActorRef], clientServers: Seq[ActorRef]): Props = {
    Props(classOf[LockServer], storeServers, clientServers)
  }
}