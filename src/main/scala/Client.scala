package client

import akka.io._ // IO, Tcp
import akka.actor.{ IO => _, _ }
import akka.util._ // ByteString
import java.net._

class Client() extends Actor with ActorLogging {

  import Tcp._ // WriteCommand, Write
  import context.system

  val remote = new InetSocketAddress("ngircd.w3.org", 6667)
  IO(Tcp) ! Connect(remote)

  def receive = {
    case CommandFailed(_: Connect) =>
      context stop self
 
    case c @ Connected(remote, local) =>
      val connection = sender

      val stage: PipelineStage[PipelineContext, String, Tcp.Command, String, Tcp.Event] =
        new StringByteStringAdapter("utf-8") >>
        new DelimiterFraming(maxSize = 1024, delimiter = ByteString("\r\n"), includeDelimiter = false) >>
        new TcpReadWriteAdapter

      val init = TcpPipelineHandler.withLogger(log, stage)

      val handler = context.actorOf(TcpPipelineHandler.props(init, connection, self))

      connection ! Register(handler)
      connection ! Write(ByteString("PASS foobar\r\n"))
      connection ! Write(ByteString("NICK client\r\n"))
      connection ! Write(ByteString("USER client client ngircd.w3.org :Client\r\n"))
      connection ! Write(ByteString("JOIN #scalaio\r\n"))

      /* see: http://mybuddymichael.com/writings/a-regular-expression-for-irc-messages.html
       *  :<prefix> <command> <params> :<trailing>
       */
      val r = """^(?:[:](\S+) )?(\S+)(?: (?!:)(.+?))?(?: [:](.+))?$""".r

      context become {
        case CommandFailed(w: Write) => // O/S buffer was full

        // After <PingTimeout> seconds of inactivity the server will send a      
        // PING to the peer to test whether it is alive or not.
        case init.Event(r(null, "PING", null, message)) =>
          println("@@ got PING $message")
          connection ! Write(ByteString(s"PONG :$message\r\n"))

        case init.Event(data) =>
          println("<< " + data)

        case _: ConnectionClosed => context.stop(self)
      }

  }

}

object Main {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("tcp")
    system.actorOf(Props(classOf[Client]))
    readLine()
    system.shutdown()
  }

}
