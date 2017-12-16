## RPC Server and RPC Client usage

As we mentioned at the end of the last section, we are:

* Bootstrapping an RPC Server (on port `8080`, for example). Before doing that, we need to bind the previous service to this server.
* Invoke the service from the client side, using the auto-generated client for the previously defined protocol.

For both challenges, we need to apply effects to an specific concurrent Monad. We've chosen `cats.effect.IO`, from [cats-effect](https://github.com/typelevel/cats-effect), but it could be any other. For this reason, we are adding the sbt dependency to our [build.sbt](./build.sbt) file, which will be provided transitively by [frees-async-cats-effect](https://github.com/frees-io/freestyle/tree/master/modules/async/async-cats-effect/shared/src) Freestyle integration. This integration basically provides an `AsyncContext` for `cats.effect.IO`.

Add the following to the `commonSettings`:

```scala
"io.frees"  %% "frees-async-cats-effect" % freesV
```

We haven't finished with the [build.sbt](./build.sbt) file yet, since we need to add a new sbt module to place our RPC Server code:

```scala
// RPC Server.
lazy val server = project
  .in(file("server"))
  .settings(moduleName := "rpc-server")
  .settings(commonSettings)
  .aggregate(services)
  .dependsOn(services)
```

### RPC Server

All the code for this subsection will be placed at `server.scala` in [./server/src/main/scala/](./server/src/main/scala/).

* Concurrent Monad definition. In this case, we can add a type alias for simplicity. This might be placed inside a `package object`, for example:

```scala
package scalaexchange

import cats.effect.IO

package object serverapp {

  type ConcurrentMonad[A] = IO[A]

  val port: Int = 8080

}
```

* Implicit Runtime evidences. At the very least, we need to provide:
  * An `FSHandler` (Natural Transformation) instance of the service defined before.
  * Server configuration, where we will bind the `RFMAnalysis` service and configure the tcp port to `8080`.

  This is done at `implicits.scala` in [./server/src/main/scala/](./server/src/main/scala/)

```scala
package scalaexchange
package serverapp

import cats.{~>, Applicative}
import freestyle.rpc.server.handlers.GrpcServerHandler
import freestyle.rpc.server._
import freestyle.rpc.server.implicits._
import scalaexchange.services.protocol.RFMAnalysisService

import scalaexchange.services.runtime.RFMAnalysisServiceHandler

trait Implicits extends scalaexchange.CommonImplicits {

  implicit def rfmAnalisysServiceHandler[F[_]: Applicative]: RFMAnalysisServiceHandler[F] =
    new RFMAnalysisServiceHandler[F]

  val grpcConfigs: List[GrpcConfig] = List(
    AddService(
      RFMAnalysisService.bindService[ConcurrentMonad]
    )
  )

  implicit def grpcServerHandler: GrpcServer.Op ~> ConcurrentMonad =
    new GrpcServerHandler[ConcurrentMonad] andThen
      new GrpcKInterpreter[ConcurrentMonad](ServerW(port, grpcConfigs).server)

}
```

Notice `import freestyle.rpc.server.implicits._`, which is providing all we need to bootstrap the server, in terms of common async instances and so on.

* Finally, how would the **Server** look like?
  Have a look at `ServerApp.scala` in [./server/src/main/scala/](./server/src/main/scala/)


```scala
package scalaexchange
package serverapp

import freestyle._
import freestyle.rpc.server._
import freestyle.rpc.server.implicits._

object ServerApp extends scalaexchange.serverapp.Implicits {

  def main(args: Array[String]): Unit =
    server[GrpcServerApp.Op].interpret[ConcurrentMonad].unsafeRunSync()

}
```

To run the server, just type:

```bash
sbt server/run
```

### App Client Demo

Now, let's see how the auto-generated client can be used to invoke this service. We'll do this in our [app](./app/src/main/scala) sbt module.

First, let's define some instances needed to setup the client, like the port where the server is listening to the incoming connections.

Let's start with `implicits.scala` in [./app/src/main/scala/](./app/src/main/scala/)

```scala
package scalaexchange
package app

import cats.effect.IO
import freestyle.rpc.client._
import io.grpc.ManagedChannel

trait Implicits extends scalaexchange.CommonImplicits {

  val channelFor: ManagedChannelFor = ManagedChannelForAddress("localhost", 8080)

  val channelConfigList: List[ManagedChannelConfig] = List(UsePlaintext(true))

  val managedChannelInterpreter =
    new ManagedChannelInterpreter[IO](channelFor, channelConfigList)

  val channel: ManagedChannel = managedChannelInterpreter.build(channelFor, channelConfigList)

}
```

Then, let's use the auto-derived client based on the protocol definition of the service.

See `AppRFMClient.scala` in [./app/src/main/scala/](./app/src/main/scala/)

```scala
package scalaexchange
package app

import cats.effect.IO
import freestyle.rpc.protocol.Empty
import freestyle.asyncCatsEffect.implicits._
import freestyle.rpc.client.implicits._

import scalaexchange.services.protocol._

object AppRFMClient extends Implicits {

  def main(args: Array[String]): Unit = {

    implicit val rfmClient: RFMAnalysisService.Client[IO] =
      RFMAnalysisService.client[IO](channel)

    val result: SegmentList = rfmClient.segments(Empty).unsafeRunSync

    println(s"\n${result.list.mkString("\n")}\n")
  }

}
```

Keep in mind two important _imports_ that are making this program interpretation possible by the `ConcurrentMonad`, which is a `cats.effect.IO` type alias, as we mentioned earlier:

```
import freestyle.asyncCatsEffect.implicits._ // From frees-async-cats-effect
import freestyle.rpc.client.implicits._      // From frees-rpc
```

If you ran the server in the previous step, you can now test this client by typing the following command:

```bash
sbt "app/runMain scalaexchange.app.AppRFMClient"
```
