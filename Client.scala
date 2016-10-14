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

class Client (val myNodeID: Int) extends Actor {
  val generator = new scala.util.Random

  def receive() = {
    case Command() =>
      command
  }

  private def command() = {
    val sample = generator.nextInt(100)
    if (sample <= 100) {
      lock_server ! Acquire("MASTER",myNodeID)
    } else if (sample > 40 & sample < 60 ) {
      lock_server ! Acquire("READ",myNodeID)
    } else {
      lock_server ! Acquire("WRITE",myNodeID)
    }
  }
}

object Client {
  def props(myNodeID: Int): Props = {
    Props(classOf[Client], myNodeID)
  }
}