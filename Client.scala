package rings

import akka._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet


// sealed trait GroupServiceAPI
// Actors send the following commands to GroupServers
// case class JoinGroup(senderNodeID: BigInt, groupId: BigInt) extends GroupServiceAPI
// case class LeaveGroup(senderNodeID: BigInt, groupId: BigInt) extends GroupServiceAPI
// case class Multicast(senderNodeID: BigInt, groupId: BigInt, msg: String) extends GroupServiceAPI

// GroupServers send the following command to Actors
// case class Message(senderNodeID: BigInt, message: String) extends GroupServiceAPI

// class GroupCell(var groupId: BigInt, var groupMemberIds: HashSet[BigInt])

class Client (val myNodeID: BigInt) extends Actor {
  val generator = new scala.util.Random
  var lock_server: Option[ActorRef] = None

  def receive() = {
    // case Update(e) =>
    //   update(e)
    case Command() =>
      command()
    case View(e) =>
      lock_server = e
      println(s"lock_server assigned")
  }

  // private def update(e: ActorRef) = {
    // lock_server = e
  // }

  private def command() = {
    val sample = generator.nextInt(100)
    println(s"command called")
    // if (sample <= 100) {
    //   lock_server ! Acquire("MASTER",myNodeID)
    // } else if (sample > 40 & sample < 60 ) {
    //   lock_server ! Acquire("READ",myNodeID)
    // } else {
    //   lock_server ! Acquire("WRITE",myNodeID)
    // }
  }
}

object Client {
  def props(myNodeID: BigInt): Props = {
    Props(classOf[Client], myNodeID)
  }
}