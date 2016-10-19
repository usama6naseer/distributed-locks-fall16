package rings

import akka._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, TimeoutException}
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet



// LockClient sends these messages to LockServer
sealed trait LockServiceAPI
case class Acquire(lockId: Lock, senderId: BigInt) extends LockServiceAPI
case class Release(lockId: Lock, senderId: BigInt) extends LockServiceAPI
case class Renew(lockId: Lock, senderId: BigInt) extends LockServiceAPI
case class RecallAck(lockId: Lock) extends LockServiceAPI

// Responses to the LockClient
sealed trait LockResponseAPI
case class LockGranted(lock: Lock) extends LockResponseAPI

// Required classes
class LockCell(var lock: Lock, var clientID: BigInt)

// Might have synchronization issues here
class LockTimeout(val lock: Lock, val clientId: BigInt, clientServers: Seq[ActorRef], t: Int, cellstore: KVInterface) extends Thread {
  implicit val timeout = Timeout(t seconds)

  private var execute = true
  override def run() = {
    stallExecution()
    if(execute) {
      try {
        val future = ask(clientServers(clientId.toInt), Recall(lock)).mapTo[Release]
        Await.result(future, timeout.duration)
        cellstore.directWrite(lock.symbolicName, null)
      } catch {
        case te: TimeoutException =>
          cellstore.directWrite(lock.symbolicName, null)
      }
    }
  }

  def stopExecuting() = {
    this.execute = false
  }

  def stallExecution() = {
    Thread.sleep(timeout.duration.length * 1000)
  }
}

class KVInterface(cellstore: KVClient) {

  // Code to access KVClient
  def directRead(key: String): Option[LockCell] = {
    val result = cellstore.directRead(findHash(key))
    if (result.isEmpty) None
    else
      Some(result.get.asInstanceOf[LockCell])
  }

  def directWrite(key: String, value: LockCell): Option[LockCell] = {
    val result = cellstore.directWrite(findHash(key), value)
    if (result.isEmpty) None
    else
      Some(result.get.asInstanceOf[LockCell])
  }

  // to convert lock name string to bigint hash
  import java.security.MessageDigest
  private def findHash(lc: String): BigInt = {
    val md: MessageDigest = MessageDigest.getInstance("MD5")
    val digest: Array[Byte] = md.digest(lc.getBytes)
    BigInt(1, digest)
  }
}

class LockServer (storeServers: Seq[ActorRef], t: Int) extends Actor {
  val generator = new scala.util.Random
  val cellstore = new KVInterface(new KVClient(storeServers))
  val lockTimeoutCache = new mutable.HashMap[String, LockTimeout]()
  var clientServers: Seq[ActorRef] = null
  implicit val timeout = Timeout(t seconds)

  /**
    * View: Primes the LockServer with a view of all the clients
    * Acquire: Client request for lock, should always return to the client
    *
    * @return
    */
  def receive() = {
    case View(clients: Seq[ActorRef]) =>
      clientServers = clients
    case Acquire(lock: Lock, id: BigInt) =>
      acquire(sender, lock, id)
    case Release(lock: Lock, id: BigInt) =>
      release(lock, id)
    case Renew(lock: Lock, id: BigInt) =>
      renew(lock, id)
  }

  def acquire(client: ActorRef, lock: Lock, id: BigInt) = {
    val name = lock.symbolicName
    println(s"Acquire request from: $id for lock: $name")
    val cell = cellstore.directRead(lock.symbolicName)
    if (!cell.isEmpty) {
      val lc = cell.get
      if(lc != null) {
        removeTimeout(lock, lc.clientID) // Remove the timeout because the locks about to get a new one
        if(lc.clientID != id) {
          val future = ask(clientServers(lc.clientID.toInt), Recall(lock)).mapTo[Release]
          Await.result(future, timeout.duration)
        }
      }
    }
    cellstore.directWrite(lock.symbolicName, new LockCell(lock, id)) // Sequence for writing the new lock
    client ! LockGranted(lock)
    addTimeout(lock, id)
  }

  def release(lock: Lock, id: BigInt) = {
    val name = lock.symbolicName
    println(s"Release request from: $id for lock: $name")
    val cell = cellstore.directRead(lock.symbolicName)
    if (!cell.isEmpty) {
      var lc = cell.get
      if(lc != null && lc.clientID == id) {
        cellstore.directWrite(lock.symbolicName, null)
        removeTimeout(lock, id)
      }
    }
  }

  private def renew(lock: Lock, id: BigInt): Unit = {
    if(lockTimeoutCache.contains(lock.symbolicName)) {
      var lt = lockTimeoutCache.get(lock.symbolicName).get
      if(lt.clientId == id) {
        lt.stallExecution()
      }
    }
  }

  private def removeTimeout(lock: Lock, id: BigInt): Unit = {
    if(lockTimeoutCache.contains(lock.symbolicName)) {
      var lt = lockTimeoutCache.get(lock.symbolicName).get
      if(lt.clientId == id) {
        lockTimeoutCache.get(lock.symbolicName).get.stopExecuting // Is this neccessary? Did I spell neccessary correct?
        lockTimeoutCache.remove(lock.symbolicName)
      }
    }
  }

  private def addTimeout(lock: Lock, id: BigInt): Unit = {
    val lt = new LockTimeout(lock: Lock, id, clientServers, t, cellstore)
    lockTimeoutCache.put(lock.symbolicName, lt)
    lt.start()
  }

  private def clientFailure(): Boolean = {
    return false
//    val sample = generator.nextInt(100)
//    sample <= 15
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