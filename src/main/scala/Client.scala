package client

import akka.io._ // IO, Tcp
import akka.actor.{ IO => _, _ }
import akka.util._ // ByteString
import java.net._

class Client() extends Actor with ActorLogging {

  import Tcp._ // WriteCommand, Write
  import context.system

  val remote = new InetSocketAddress("www.w3.org", 80)
  IO(Tcp) ! Connect(remote)

  def receive = {
    case CommandFailed(_: Connect) =>
      context stop self
 
    case c @ Connected(remote, local) =>
      val connection = sender

      val stage: PipelineStage[PipelineContext, String, Tcp.Command, String, Tcp.Event] =
        /* Simple convenience pipeline stage for turning Strings into
         * ByteStrings and vice versa.
         */
        new StringByteStringAdapter("utf-8") >>
        /* Pipeline stage for delimiter byte based framing and
         * de-framing. Useful for string oriented protocol using '\n'
         * or 0 as delimiter values.
         */
        new DelimiterFraming(maxSize = 1024, delimiter = ByteString("\n"), includeDelimiter = false) >>
        /* Adapts a ByteString oriented pipeline stage to a stage that
         * communicates via Tcp Commands and Events. Every ByteString
         * passed down to this stage will be converted to Tcp.Write
         * commands, while incoming Tcp.Receive events will be
         * unwrapped and their contents passed up as raw ByteStrings.
         */
        new TcpReadWriteAdapter


      /* This class wraps up a pipeline with its external (i.e. “top”)
       * command and event types and providing unique wrappers for
       * sending commands and receiving events (nested and non-static
       * classes which are specific to each instance of Init). All
       * events emitted by the pipeline will be sent to the registered
       * handler wrapped in an Event.
       */
      val init = TcpPipelineHandler.withLogger(log, stage)

      /* This actor wraps a pipeline and forwards commands and events
       * between that one and a Tcp connection actor. In order to
       * inject commands into the pipeline send an
       * TcpPipelineHandler.Init.Command message to this actor; events
       * will be sent to the designated handler wrapped in
       * TcpPipelineHandler.Init.Event messages.
       * 
       * When the designated handler terminates the TCP connection is
       * aborted. When the connection actor terminates this actor terminates as
       * well; the designated handler may want to watch this actor’s lifecycle.
       */
      val handler = context.actorOf(TcpPipelineHandler.props(init, connection, self))

      connection ! Register(handler)
      connection ! Write(ByteString("GET / HTTP/1.0\r\n\r\n"))

      context become {
        case CommandFailed(w: Write) => // O/S buffer was full

        case init.Event(data) =>
          println("@@@@ " + data)

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
