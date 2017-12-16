package scalaexchange
package app

import cats.effect.IO
import freestyle.rpc.protocol.Empty
import freestyle.asyncCatsEffect.implicits._ // From frees-async-cats-effect
import freestyle.rpc.client.implicits._      // From frees-rpc

import scalaexchange.services.protocol._

object AppRFMClient extends Implicits {

  def main(args: Array[String]): Unit = {

    implicit val rfmClient: RFMAnalysisService.Client[IO] =
      RFMAnalysisService.client[IO](channel)

    val result: SegmentList = rfmClient.segments(Empty).unsafeRunSync

    println(s"\n${result.list.mkString("\n")}\n")
  }

}