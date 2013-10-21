package client

import akka.io._ // IO, Tcp
import akka.actor.{ IO => _, _ }
import akka.util._ // ByteString
import java.net._
import javax.net.ssl._

class Client() extends Actor with ActorLogging {

  import Tcp._ // WriteCommand, Write
  import context.system

  val remote = new InetSocketAddress("tightlips.w3.org", 6667)
  IO(Tcp) ! Connect(remote)

  val sslEngine: SSLEngine = {
    val context = SSLContext.getInstance("TLSv1.1")
    val trustManagers: Array[TrustManager] = Array(new X509TrustManager {
      import java.security.cert.X509Certificate
      def checkClientTrusted(arg0: Array[X509Certificate], arg1: String): Unit = ()
      def checkServerTrusted(arg0: Array[X509Certificate], arg1: String): Unit = ()
      def getAcceptedIssuers(): Array[X509Certificate] = Array()
    })
    context.init(null, trustManagers, null)
    val engine = context.createSSLEngine(remote.getHostName, remote.getPort)
    engine.setUseClientMode(true)
    engine
  }

  def receive = {
    case CommandFailed(_: Connect) =>
      context stop self
 
    case c @ Connected(remote, local) =>
      val connection = sender

      val stage =
        new StringByteStringAdapter("utf-8") >>
        new DelimiterFraming(maxSize = 1024, delimiter = ByteString("\r\n"), includeDelimiter = false) >>
        new TcpReadWriteAdapter // @@ >> new SslTlsSupport(sslEngine)

      val init = TcpPipelineHandler.withLogger(log, stage)

      val handler = context.actorOf(TcpPipelineHandler.props(init, connection, self))

      connection ! Register(handler)

      def send(msg: String): Unit =
        handler ! init.Command(s"$msg\r\n")

      send("PASS foobar")
      send("NICK client")
      send("USER client client ngircd.w3.org :Client")
      send("JOIN #scalaio")

      /* see: http://mybuddymichael.com/writings/a-regular-expression-for-irc-messages.html
       *  :<prefix> <command> <params> :<trailing>
       */
      val r = """^(?:[:](\S+) )?(\S+)(?: (?!:)(.+?))?(?: [:](.+))?$""".r

      context become {
        case CommandFailed(w: Write) => // O/S buffer was full

        // After <PingTimeout> seconds of inactivity the server will send a      
        // PING to the peer to test whether it is alive or not.
        case init.Event(r(null, "PING", null, message)) =>
          println(s"@@ got PING $message")
          send(s"PONG :$message")

        case init.Event(e@r(_, "INVITE", params, _)) =>
          println(s"@@ $e")
          val Array(nickname, channel) = params.split(" ")
          send(s"JOIN $channel")

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
