package rings

import akka.event.Logging
import akka._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet


sealed trait GroupServiceAPI

// GroupServers send the following command to Actors
case class Message(senderNodeID: BigInt, message: String) extends GroupServiceAPI

class GroupCell(var groupId: BigInt, var groupMemberIds: HashSet[BigInt])

class GroupServer (lockServer: ActorRef, timeout: Int, id: BigInt) extends Actor {
  val generator = new scala.util.Random
  var queue = new mutable.Queue[Lock]()
  var myLock: Lock = null
  val lockClient = new LockClient(lockServer, timeout)
  private var count = 0
  private val lockArr = Array("Hello","My", "Name", "Is")

  def receive() = {
    case Prime() =>
      allocCell
    case Command() =>
      command
    case Recall(lock) =>
      recall(sender, lock)
  }

  private def allocCell() = {}

  private def recall(sender:ActorRef, lock: Lock): Unit = {
    val name = lock.symbolicName
    println(s"Node: $id, Recall Lock: $name")
    lockClient.release(sender, lock, id, true)
    myLock = null
  }

  private def command(): Unit = {
    if (myLock == null) {
      println(s"Lock is null, getting a new lock")
      myLock = lockClient.acquire(lockArr(id.toInt), id)
    } else {
      val sample = generator.nextInt(100)
      if (sample < 80) {
        println(s"Renewing lock")
        myLock = lockClient.acquire(myLock.symbolicName, id)
      }
      else {
        var name = myLock.symbolicName
        println(s"Releasing lock $name")
        lockClient.release(myLock, id, false)
        myLock = lockClient.acquire(getRandomString(), id)
        name = myLock.symbolicName
        println(s"Acquired new lock $name")
      }
    }
  }

  private def getRandomString(): String = {
    lockArr(generator.nextInt(lockArr.length))
  }

}
object GroupServer {
  def props(lockServer: ActorRef, timeout: Int, id: BigInt): Props = {
    Props(classOf[GroupServer], lockServer, timeout, id)
  }
}