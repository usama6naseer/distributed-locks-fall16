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
  val num_of_clients = 10

  val clients = for (i <- 0 until num_of_clients-1)
      yield system.actorOf(Client.props(i), "Client" + i)
  
  val stores = for (i <- 0 until num_of_clients-1)
      yield system.actorOf(KVStore.props(), "Store" + i)
  
  val lock_server = system.actorOf(LockServer.props(clients), "LockServer")

  def main(args: Array[String]): Unit = run()

  def run(): Unit = {
    val s = System.currentTimeMillis
    runUntilDone
    val runtime = System.currentTimeMillis - s
    println(s"Done in $runtime ms")
    system.shutdown()
  }

  def runUntilDone() = {
    for (c <- clients) {
      c ! Command()
      // lock_server ! Acquire("MASTER", c,getID())
    }
    // val future = ask(master, Join()).mapTo[Stats]
    // master ! Start(opsPerNode)
    // val done = Await.result(future, 60 seconds)
    // println(s"Final stats: $done")
  }

}
