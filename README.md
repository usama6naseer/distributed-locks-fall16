# Lab 2: Distributed Locks
Our lock service employs greedy caching and ensures liveness across all clients and locks. This means that the LockClient will cache every lock it receives and that the LockServer will ensure all LockClients that request a lock receive one. 

## LockClient
The LockClient exposes an API consisting of _acquire_ and _release_ functions. The LockClient is instantiated with a timeout _t_ and an ActorRef for the LockServer. When acquiring a lock for the first time, the Client will send a synchronous _acquire_ command to the server. Upon receiving the lock, the client will store the lock in its cache. If the lock is already in the cache, the client will return the lock and send an asynchronous KeepAlive message to the server. 

When releasing a lock, the client will always remove the lock from its cache before sending an asynchronous Release message to the server. 

## LockServer
The LockServer handles the majority of interactions with the client asynchronously. The LockServer ensures that whenever a LockClient requests a lock, the Client eventually receives it. The LockServer accomplishes this by sending a synchronous Recall message to any server holding the requested lock, before replying to the LockClient who requested it. 

The LockServer uses the Akka scheduler to keep track of timeouts. Whenever a LockClient acquires a lock, the LockServer will schedule a _recall_ command to run at the end of the specified timeout. If the LockServer receives a KeepAlive message from a LockClient before the end of its timeout, the LockServer will cancel the original timeout and instantiate a new one. 

## ClientService
Our client service displays an implementation of how to use the LockService. The Client must be an Akka Actor capable of handling a Recall message. Upon receiving the Recall message, the Client must finish executing its code before relinquishing the lock. The Client can reply to the _recall_ through the LockClient's _release_ function that allows for the specification of the sender. 

## Timeouts
Client's lease with the lock server exists for a specific amount of time i-e timeout. Our system can be tested for various timeout values by changing the variable 't' in TestHarness.scala file. This timeout value is used by lock server to detect lease expiration.
As expected, decreasing the timeout value resulted in more clients' time-outs (lease expiration). Since our tests (command() function in ClientService.scala) selects between acquire, renew and release in a random manner, there is lower chance for a client to renew its lock lease if the timeout value is smaller. Increasing the timeout decreases the chance of lease expiring due to greater chance of renew being called by command() in ClientService.scala for a particular client.

## Current Issues
Our code is not perfect and will run into synchronization and timeout errors. The synchronization error occurs when the Client does not respond to the Recall message soon enough (or at all). This keeps the lock within the Client's cache when it should be removed. A solution is to always release the lock ASAP. Our server does not handle multiple concurrent requests well and can get overloaded if messages are sent in bursts from several clients. 

Our code could additionally be improved by using the KVStore to maintain the locks. In a real-world setting, the locks would have to be maintained using persistent storage. The code could also be improved by granting clients the option to respond to timeout Recall messages with a KeepAlive message. 

