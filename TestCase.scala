package rings

import scala.concurrent.duration._
import scala.concurrent.Await

import akka.actor.{Actor, ActorSystem, ActorRef, Props}
import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout

object TestCase {
  val system = ActorSystem("Rings")
  implicit val timeout = Timeout(60 seconds)

  // create lock server
  var num_of_clients = 10
  var clients: Option[Seq[ActorRef]] = None
  var stores: Option[Seq[ActorRef]] = None
  var lock_server: Option[ActorRef] = None
  
  def main(args: Array[String]): Unit = run()

  def run(): Unit = {
    val s = System.currentTimeMillis
    initialize(num_of_clients)
    runUntilDone
    val runtime = System.currentTimeMillis - s
    println(s"Done in $runtime ms")
    system.shutdown()
  }

  def initialize(num: BigInt) = {
    clients = for (i <- 0 until num_of_clients-1)
      yield system.actorOf(Client.props(i), "Client" + i)
  
    stores = for (i <- 0 until num_of_clients-1)
      yield system.actorOf(KVStore.props(), "Store" + i)
  
    lock_server = system.actorOf(LockServer.props(clients), "LockServer") 

    for (c <- clients) {
      c ! View(lock_server)
    }   
  }

  def runUntilDone() = {
    for (c <- clients) {
      // c ! View(e)
      c ! Command()
      // lock_server ! Acquire("MASTER", c,getID())
    }
    // val future = ask(master, Join()).mapTo[Stats]
    // master ! Start(opsPerNode)
    // val done = Await.result(future, 60 seconds)
    // println(s"Final stats: $done")
  }

}
