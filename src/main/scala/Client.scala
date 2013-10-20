package irc

import akka.io._ // IO, Tcp
import akka.actor.{ IO => _, _ }
import akka.util._ // ByteString
import java.net._

class Client() extends Actor {

  import Tcp._ // WriteCommand, Write
  import context.system

  /* IO(Tcp) == IO manager
   * 
   * The manager is an actor that handles the underlying low level I/O
   * resources (selectors, channels) and instantiates workers for
   * specific tasks, such as listening to incoming connections.
   * 
   * The first step of connecting to a remote address is sending a
   * Connect message to the TCP manager; in addition to the simplest
   * form shown above there is also the possibility to specify a local
   * InetSocketAddress to bind to and a list of socket options to
   * apply.
   */
  val remote = new InetSocketAddress("www.w3.org", 80)
  IO(Tcp) ! Connect(remote)

  def receive = {
    /* The TCP manager will then reply either with a CommandFailed...
     */
    case CommandFailed(_: Connect) =>
      context stop self
 
    /* ... or it will spawn an internal actor representing the new
     * connection. This new actor will then send a Connected message
     * to the original sender of the Connect message.
     */
    case c @ Connected(remote, local) =>
      /* The connection actor watches the registered handler and closes the
       * connection when that one terminates, thereby cleaning up all
       * internal resources associated with that connection.
       */
      val connection = sender

      /* In order to activate the new connection a Register message must be
       * sent to the connection actor, informing that one about who
       * shall receive data from the socket. Before this step is done
       * the connection cannot be used, and there is an internal
       * timeout after which the connection actor will shut itself
       * down if no Register message is received.
       */
      connection ! Register(self)

      /* The simplest WriteCommand implementation which wraps a ByteString
       * instance and an "ack" event. A ByteString (as explained in
       * this section) models one or more chunks of immutable
       * in-memory data with a maximum (total) size of 2 GB (2^31
       * bytes).
       */
      connection ! Write(ByteString("GET / HTTP/1.0\r\n\r\n"))

      /* The actor in the example uses become to switch from unconnected to
       * connected operation, demonstrating the commands and events
       * which are observed in that state.
       */
      context become {
        case CommandFailed(w: Write) => // O/S buffer was full

        case Received(data) =>
          println(data.utf8String)

        case _: ConnectionClosed => context.stop(self)
      }

  }

}

object Main {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("irc")
    system.actorOf(Props(classOf[Client]))
    readLine()
    system.shutdown()
  }

}
