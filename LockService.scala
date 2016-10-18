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


// LockClient sends these messages to LockServer
sealed trait LockServiceAPI
case class Acquire(lockId: Lock, senderId: BigInt) extends LockServiceAPI
case class Release(lockId: Lock, senderId: BigInt) extends LockServiceAPI
case class Renew(lockId: Lock, senderId: BigInt) extends LockServiceAPI

// Responses to the LockClient
sealed trait LockResponseAPI
case class LockGranted(lock: LockAPI)

// Required classes
class LockCell(var id: BigInt, var lock: Lock)

class LockTimeout(lock: Lock, client: ActorRef, timeout: Timeout, lockServer: LockServer) extends Thread {

  private var execute = true
  override def run() = {
    Thread.sleep(timeout.duration.length)
    if(execute) {
      val future = ask(clientServers(id.toInt), Recall(lock)).mapTo[LockResponseAPI]
      Await.result(future, timeout.duration)
      lockServer.directWrite(findHash(lock.symbolicName), new LockCell(null, lock))
    }
  }

  def stopExecute() = {
    execute = false
  }

}

class LockServer (storeServers: Seq[ActorRef], timeout: Int) extends Actor {
  val cellstore = new KVClient(storeServers)
  var clientServers: Seq[ActorRef] = None
  implicit val timeout = Timeout(timeout seconds)
  private val lockTimeoutCache = new mutable.HashMap[String, Lock]()
  private val Int threshold = 90

  /**
    * View: Primes the LockServer with a view of all the clients
    * Acquire: Client request for lock, should always return to the client
    *
    * @return
    */
  def receive() = {
    case View(e) =>
      clientServers = Some(e).get
    case Acquire(lock: Lock, id: BigInt) =>
      if(communicateWithClient()) acquire(lock, id)
    case Release(lock: Lock, id: BigInt) =>
      if(communicateWithClient()) release(lock, id)
    case Renew(lock: Lock, id: BigInt) =>
      if(communicateWithClient()) renew(lock, id)
  }

  def acquire(lock: Lock, id: BigInt) = {

    val cell = directRead()
    if (cell.isEmpty) {
      directWrite(findHash(), new LockCell(id, lock))
    } else {
      val lc = cell.get
      if(lc.id == null) {
        directWrite(findHash(lock.symbolicName), new LockCell(id, lock))
      } else if(lc.id != id) {
        val future = ask(clientServers(id.toInt), Recall(lock)).mapTo[LockResponseAPI]
        val result = Await.result(future, timeout.duration)
        directWrite(findHash(lock.symbolicName), new LockCell(id, lock))
      }
    }
    renew(lock, id)
    clientServers(id.toInt) ! LockGranted(lock)
  }

  def release(lock: Lock, id: BigInt) = {
    val cell = directRead(findHash(lock.symbolicName))
    if (!cell.isEmpty) {
      var lc = cell.get
      if(lc.id != null && lc.id == id) {
        directWrite(findHash(lock.symbolicName), new LockCell(null, lock))
        removeTimeout(lock, id)
      }
    }
  }

  def renew(lock: Lock, id: BigInt) = {
    val lt = new LockTimeout(lock, id, timeout, this)
    lt.start()
    lockTimeoutCache.put(lock.symbolicName, lt)
  }

  private def removeTimeout(lock: Lock) = {
    if(lockTimeoutCache.contains(lock.symbolicName)) {
      lockTimeoutCache.get(lock.symbolicName).stopExecute()
      lockTimeoutCache.remove(lock.symbolicName)
    }
  }

  private def communicateWithClient(): equals(obj: scala.Any): Boolean = {
    val sample = generator.nextInt(100)
    sample <= threshold
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
  def props(storeServers: Seq[ActorRef], timeout: Int): Props = {
    Props(classOf[LockServer], storeServers, clientServers)
  }
}