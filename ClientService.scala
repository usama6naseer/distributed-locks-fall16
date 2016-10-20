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

class GroupServer (lockServer: ActorRef, timeout: Int, id: BigInt, burstSize: Int) extends Actor {
  val generator = new scala.util.Random
  var lockList = new mutable.HashSet[String]()
  val lockClient = new LockClient(lockServer, timeout)
  var stats = new Stats
  private val lockArr = Array("Hello","My", "Name", "Is", "Slim", "Shady", "Will", "You", "Please", "Stand", "Up")

  def receive() = {
    case Prime() =>
      // Do nothing
    case Command() =>
      incoming(sender)
      command
    case Recall(lock) =>
      recall(sender, lock) // Recall the lock when receiving this request (it will otherwise be timed out)
  }

  private def incoming(master: ActorRef) = {
    stats.messages += 1
    if (stats.messages >= burstSize) {
      master ! BurstAck(id.toInt, stats)
      stats = new Stats
    }
  }

  private def recall(sender:ActorRef, lock: Lock): Unit = {
    val name = lock.symbolicName
    if(lockList.contains(name)) {
      lockClient.release(sender, lock, id)
      lockList -= name
      println(s"Node: $id, Recall Lock: $name, current Locks: $lockList")
    } else {
      println(s"Node: $id, Already Removed Lock: $name, current Locks: $lockList")
    }
  }

  private def command(): Unit = {
    if (lockList.isEmpty) {
      val name = lockArr(id.toInt)
      println(s"Node: $id, Acquiring: $name")
      lockClient.acquire(lockArr(id.toInt), id)
      lockList += name
    } else {
      val sample = generator.nextInt(100)
      if (sample < 70) {
        val name = lockList.toVector(generator.nextInt(lockList.size))
        lockClient.acquire(name, id)
        println(s"Node: $id, Renewing Lock: $name, current Locks: $lockList")
      }
      else if (sample >= 70 && sample < 80) {
        val name = lockList.toVector(generator.nextInt(lockList.size))
        lockClient.release(new Lock(name), id)
        lockList -= name
        println(s"Node: $id, Released Lock: $name, current Locks: $lockList")
      } else {
        val name = getRandomString()
        lockClient.acquire(name, id)
        lockList += name
        println(s"Node: $id, Acquired Lock: $name, current Locks: $lockList")
      }
    }
  }

  private def getRandomString(): String = {
    lockArr(generator.nextInt(lockArr.length))
  }

}
object GroupServer {
  def props(lockServer: ActorRef, timeout: Int, id: BigInt, ackEach: Int): Props = {
    Props(classOf[GroupServer], lockServer, timeout, id, ackEach)
  }
}