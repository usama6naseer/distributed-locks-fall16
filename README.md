# Answer questions here

1. Do actors ever receive messages originating from a given actor out of order? What if the messages are forwarded through an intermediary?
(WITHOUT THE INTERMEDIARY) Yes, the actors receive the message out of order. This is due to the asynchronous behaviour of scala messages. When burst size is 1000 (as in the given case), the messages are totally out of order. The load master sends command messages to servers sequentially (as in first burst to server 0, then to server 1 and so on), due to asynchronous behaviour, there is no sequential order in which the messages are processed on server. When the burstSize value is increased to 100,000,000 (comparable to machine's cpu cycle frequency), the messages processing on servers becomes somewhat sequential (server 0 gets a bunch of messages before server 1) but the messages themselves have no sequential behaviour.
(WITH THE INTERMEDIARY) When forwarded through an intermediary, it is still possible for messages to be received out of order when pacleta are traversing a network; however, each message will be added to the intermediary's queue in the order in wich they are received. Assuming no latency/loss, each message will be received in the order that they are sent. Additionally, adding an intermediary forces all Actors to send their messages through a specified server (whichever GroupServer manages that given group). This solves concurrency issues faced by Actor's when joining/leaving a group at the same time. 

2. What if two actors multicast to the same group? Does each member receive the messages in the same order?
If two or more actors multicast to the same group, the members of the group do not receive the messages in the order. This could be easily seen from the test run of the code. If two servers multicast to the same group, their message sequence is interleaved and they both can have different order.

3. Do actors ever receive messages for groups "late", after having left the group?
Yes, they do get some messages late. Leaving a group is actually removing the actor from that specific group's list. Consider a case when an actor gets a command to leave a group and just after that another actor multicasts to the same group. Between the time of the remove initiation and actual removal from the list, the actor can get some messages due to interleaving of multicast messages (due to async behaviour).

4. How does your choice of weights and parameters affect the amount of message traffic?
BurstSize affects the messages sent to each GroupServer; increasing BurstSize increases the message traffic on every GroupServer. 
Similarly increasing numNodes increases message traffic, since a specific number (BurstSize) of "command" message is sent to each node to initiate either of join, remove or multicast messages.
Additionally, increasing the percent chance that "joinRandomGroup" executes when a Command is received increases the total number of messages sent by the servers. This is due to the fact that more servers are joining groups rather than leaving them. This has the effect of increasing the amount of traffic whenever a multicast message is received. 

5. How can you be sure your code is working, i.e., the right actors are receiving the right messages?
The actor is added to a random GroupServer (determined by generating a random number in the specified range). This can be done by keeping track of the member lists maintained by GroupServers in the key/value store. After each join, remove, or multicast, this list can be matched which the messages generated to see if the code is working correctly. The functionality is also verified on each multicast message. When a server receives a message, the store can be checked to make sure the sender/receiver are both members of the group. 


***********************************
Some observations:

At first, the GroupService was not using an intermediary. A lot of problems were faced in this scenario. The biggest problem was the occasional dropped JoinGroup and LeaveGroup requests. The problem was that if two actors were added to the same group at the same time, due to concurrency issues only one was added to the key store. Suppose 'L' is the list before any of them is added. First actor was added to L's local copy to modify it to L*. Similarly, second actor was added to L's local copy to modify it to L**. Now L* would not include the second actor and L** would not include the first actor. Whichever was written to the key store first was over written by the second in the key store. Same is the problem with removal of actor.
To solve this problem, GroupService is modified to set up some servers as intermediary. Each GroupServer is responsible for a certain number of groups. The server will control all of the actions for the groups it manages. In this was concurrent join/remove was resolved.

By adding the intermediary, the actor joining load is better distributed among the GroupServers. GroupServer, responsible for join/remove in a certain number of groups, distribute the incoming join requests over the range of GroupServers it is responsible for, using a similar mod technique used in RingService. In this way the actors are uniformly distributed over the range of GroupServers.

Some peculiar behaviour is seen by changing the burstSize value. For value equal to 1000 (default), there is no specific sequence in which messages are processed in servers or which server process the message first. Although the LoadMaster sends "command" message to servers in increasing order of their IDs, no specific order is seen in which server processes order first due to async behaviour. When the burstSize value is increased to 100,000,000 (comparable to machine's cpu cycle frequency), the messages processing on servers becomes somewhat sequential (server 0 gets a bunch of messages before server 1) but the messages themselves have no sequential behaviour.

A tidbit:
Adding a print statement to LoadMaster after "command" message resolves most of concurrency issues without even using an intermediary which is probably due to I/O request that OS needs to make on every for loop instance to print value (which basically make this whole event synchronous). 
