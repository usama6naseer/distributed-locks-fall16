package rings

import akka._
import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
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
case class KeepAlive(lockId: Lock, senderId: BigInt) extends LockServiceAPI

// Responses to the LockClient
sealed trait LockResponseAPI
case class LockGranted(lock: Lock) extends LockResponseAPI

// Required classes
class LockCell(var lock: Lock, var clientId: BigInt, var scheduledTimeout: Cancellable)

class LockServer (system: ActorSystem, t: Int) extends Actor {
  import system.dispatcher

  val generator = new scala.util.Random
  var lockMap = new mutable.HashMap[String, LockCell]()
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
      if(!clientFailure()) acquire(sender, lock, id)
    case Release(lock: Lock, id: BigInt) =>
      if(!clientFailure()) release(lock, id)
    case KeepAlive(lock: Lock, id: BigInt) =>
      if(!clientFailure()) keepAlive(lock, id)
  }

  def acquire(client: ActorRef, lock: Lock, clientId: BigInt) = {
    val name = lock.symbolicName
    println(s"Acquire request from: $clientId for lock: $name")
    val cell = directRead(lock.symbolicName)
    if (!cell.isEmpty) {
      val lc = cell.get
      if(lc != null) {
        lc.scheduledTimeout.cancel()
        if(lc.clientId != clientId) { // Get the lock back from this client
          recall(lock, clientId)
        }
      }
    }
    client ! LockGranted(lock)
    startTimeout(lock, clientId)
  }

  def release(lock: Lock, clientId: BigInt) = {
    val name = lock.symbolicName
    println(s"Release request from: $clientId for lock: $name")
    val cell = directRead(lock.symbolicName)
    if (!cell.isEmpty) {
      val lc = cell.get
      if(lc != null && lc.clientId == clientId) { // Make sure client owns the lock
        if(!lc.scheduledTimeout.isCancelled) lc.scheduledTimeout.cancel() // Cancel the timeout
        directWrite(lock.symbolicName, null) //
      }
    }
  }

  private def recall(lock: Lock, clientId: BigInt) = {
    try{
      val future = ask(clientServers(clientId.toInt), Recall(lock)).mapTo[LockServiceAPI]
      val response = Await.result(future, timeout.duration)
    } catch {
      case te: TimeoutException =>
        println(s"We timed out here. Yay? $clientId")
    }
    release(lock, clientId)
  }

  private def keepAlive(lock: Lock, clientId: BigInt): Unit = {
    val cell = directRead(lock.symbolicName)
    if(!cell.isEmpty) {
      val lc = cell.get
      if(lc != null && lc.clientId == clientId) {
        lc.scheduledTimeout.cancel()
        startTimeout(lock, clientId)
      }
    }
  }

  private def startTimeout(lock: Lock, id: BigInt): Unit = {
    val cancel = system.scheduler.scheduleOnce(timeout.duration) {
      recall(lock, id)
    }
    directWrite(lock.symbolicName, new LockCell(lock, id, cancel))
  }

  private def clientFailure(): Boolean = {
    val sample = generator.nextInt(100)
    sample <= 15
  }

  def directRead(key: String): Option[LockCell] = {
    val result = lockMap.get(key)
    if (result.isEmpty) None
    else
      Some(result.get.asInstanceOf[LockCell])
  }

  def directWrite(key: String, value: LockCell): Option[LockCell] = {
    val result = lockMap.put(key, value)
    if (result.isEmpty) None
    else
      Some(result.get.asInstanceOf[LockCell])
  }
}

object LockServer {
  def props(system: ActorSystem, t: Int): Props = {
    Props(classOf[LockServer], system, t)
  }
}