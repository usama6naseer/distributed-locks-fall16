package rings

import akka._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet

import java.security.MessageDigest


// LockClient sends these messages to LockServer
sealed trait LockServiceAPI
case class Acquire(lockId: Lock, senderId: BigInt) extends LockServiceAPI
case class Release(lockId: Lock, senderId: BigInt) extends LockServiceAPI
case class Renew(lockId: Lock, senderId: BigInt) extends LockServiceAPI
case class RecallAck(lockId: Lock) extends LockServiceAPI

// Responses to the LockClient
sealed trait LockResponseAPI
case class LockGranted(lock: Lock)

// Required classes
class LockCell(var client: ActorRef, var lock: Lock, var clientID: BigInt)

// Might have synchronization issues here
class LockTimeout(lock: Lock, client: ActorRef, t: Int, lockServer: LockServer) extends Thread {
  implicit val timeout = Timeout(t seconds)

  var execute = true
  override def run() = {
    Thread.sleep(timeout.duration.length)
    if(execute) {
      val future = ask(client, Recall(lock)).mapTo[RecallAck]
      Await.result(future, timeout.duration)
      lockServer.directWrite(lockServer.findHash(lock.symbolicName), null)
    }
  }
}

class LockServer (storeServers: Seq[ActorRef], t: Int) extends Actor {
  val generator = new scala.util.Random
  val cellstore = new KVClient(storeServers)
  val lockTimeoutCache = new mutable.HashMap[String, LockTimeout]()
  implicit val timeout = Timeout(t seconds)

  /**
    * View: Primes the LockServer with a view of all the clients
    * Acquire: Client request for lock, should always return to the client
    *
    * @return
    */
  def receive() = {
    case Acquire(lock: Lock, id: BigInt) =>
      if(!clientFailure()) acquire(sender, lock, id)
    case Release(lock: Lock, id: BigInt) =>
      if(!clientFailure()) release(sender, lock, id)
    case Renew(lock: Lock, id: BigInt) =>
      if(!clientFailure()) renew(sender, lock)
  }

  def acquire(client: ActorRef, lock: Lock, id: BigInt) = {
    val cell = directRead(findHash(lock.symbolicName))
    if (!cell.isEmpty) {
      val lc = cell.get
      if(lc != null && lc.clientID != id) {
        val future = ask(lc.client, Recall(lock)).mapTo[LockResponseAPI]
        Await.result(future, timeout.duration) // Waits the time duration, then overwrites the lock
      }
    }
    directWrite(findHash(lock.symbolicName), new LockCell(client, lock, id)) // Sequence for writing the new lock
    renew(client, lock) // Start the new lease
    client ! LockGranted(lock) // let the client know that the lock has been granted
  }

  def release(client: ActorRef, lock: Lock, id: BigInt) = {
    val cell = directRead(findHash(lock.symbolicName))
    if (!cell.isEmpty) {
      var lc = cell.get
      if(lc != null && lc.clientID == id) {
        directWrite(findHash(lock.symbolicName), null)
        removeTimeout(lock)
      }
    }
  }

  private def renew(client: ActorRef, lock: Lock): Unit = {
    val lt = new LockTimeout(lock, client, t, this)
    lt.start()
    lockTimeoutCache.put(lock.symbolicName, lt)
  }

  private def removeTimeout(lock: Lock): Unit = {
    if(lockTimeoutCache.contains(lock.symbolicName)) {
      lockTimeoutCache.get(lock.symbolicName).get.execute = false // Is this neccessary? Did I spell neccessary correct?
      lockTimeoutCache.remove(lock.symbolicName)
    }
  }

  private def clientFailure(): Boolean = {
    val sample = generator.nextInt(100)
    sample <= 90
  }

  // to convert lock name string to bigint hash
  def findHash(lc: String): BigInt = {
    val md: MessageDigest = MessageDigest.getInstance("MD5")
    val digest: Array[Byte] = md.digest(lc.getBytes)
    BigInt(1, digest)
  }

  // Code to access KVClient
  def directRead(key: BigInt): Option[LockCell] = {
    val result = cellstore.directRead(key)
    if (result.isEmpty) None else
      Some(result.get.asInstanceOf[LockCell])
  }

  def directWrite(key: BigInt, value: LockCell): Option[LockCell] = {
    val result = cellstore.directWrite(key, value)
    if (result.isEmpty) None else
      Some(result.get.asInstanceOf[LockCell])
  }

//  def checkMaster(lock: LockAPI, id: BigInt): Unit = master_lock.synchronized  {
//
//    val cell = directRead(findHash("MASTER"))
//    if (cell.isEmpty) {
//      // makeMaster(id)
//      var lcc = new LockCell("MASTER", id) // hardcoded for master since this function is only called for master selection
//      directWrite(findHash("MASTER"), lcc)
//      // inform client of the master
//      clientServers(id.toInt) ! InformClientMasterChange(id)
//    } else {
//      var lc = cell.get
//      var master_id = lc.lockHolders
//      if (master_id == -1) {
//        // makeMaster(id)
//        var lcc = new LockCell("MASTER", id) // hardcoded for master since this function is only called for master selection
//        directWrite(findHash("MASTER"), lcc)
//        // inform client of the master
//        clientServers(id.toInt) ! InformClientMasterChange(id)
//
//      }
//      else {
//        // println(s"******* $master_id is already master")
//        // inform client of the master
//        clientServers(id.toInt) ! InformClientMasterSame(master_id)
//      }
//    }
//  }
//
//  def release(lockId: String, myNodeID: BigInt): Unit = master_lock.synchronized {
//    // three cases for locks i-2 master, read write
//    if (lockId == "MASTER") {
//      var cell = directRead(findHash("MASTER"))
//      if (cell.isEmpty) {
//        clientServers(myNodeID.toInt) ! InformClientMasterRelease(-1) // return -1 to client since it cannot release a lock it doesn't have
//      }
//      else {
//        var gc = cell.get
//        var m_id = gc.lockHolders
//        if (m_id != myNodeID) {
//          clientServers(myNodeID.toInt) ! InformClientMasterRelease(-1) // return -1 to client since it cannot release a lock it doesn't have
//        }
//        else {
//          var lc = new LockCell("MASTER", -1) // -1 represents master lock released case
//          directWrite(findHash("MASTER"), lc)
//          // inform client of the master
//          clientServers(myNodeID.toInt) ! InformClientMasterRelease(myNodeID)
//        }
//      }
//    }
//  }

}
object LockServer {
  def props(storeServers: Seq[ActorRef], t: Int): Props = {
    Props(classOf[LockServer], storeServers, t)
  }
}