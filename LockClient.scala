package rings

import scala.concurrent.duration._
import scala.concurrent._
import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.mutable

// LockServer send the following command to LockClient
sealed trait LockClientAPI
case class Recall(lock: Lock) extends LockClientAPI

// Lock Class
class Lock(val symbolicName: String)

// Internal cache for storing valid locks
/**
  * LockClient implements a client's interface to the LockServer, with a lock cache.
  * Instantiate one LockClient for each actor that is a client of the LockServer.
  */
class LockClient(lockServer: ActorRef, t: Int) {
  private val lockCache = new mutable.HashMap[String, Lock]()
  implicit val timeout = Timeout(t seconds)

  /**
    * Acquire acts as an interface for both acquiring and renewing a lock.
    * @param symbolicName name of lock
    * @return Lock object representing the lock
    */
  def acquire(symbolicName: String, id: BigInt): Lock = {
    if(lockCache.contains(symbolicName)) {
      val lock = lockCache.get(symbolicName).get
      lockServer ! Renew(lock, id)
      lock
    } else {
      val lock = new Lock(symbolicName)
      try {
        val future = ask(lockServer, Acquire(lock, id)).mapTo[LockResponseAPI]
        Await.result(future, timeout.duration)
      } catch {
        case te: TimeoutException =>
          acquire(symbolicName, id)
      }
      lockCache.put(symbolicName, lock)
      lock
    }
  }

  /**
    * Removes the Lock from the cache if it exists there; sends notification to the lock server that
    * it is relinquishing the lock
    * @param lock Lock object to be released
    */
  def release(lock: Lock, id: BigInt): Unit = {
    release(lockServer, lock, id)
  }

  def release(sender: ActorRef, lock: Lock, id: BigInt): Unit = {
    // Notify the lock server that it is releasing said lock
    if(lockCache.contains(lock.symbolicName)) {
      lockCache.remove(lock.symbolicName)
    }
    sender ! Release(lock, id)
  }
}
