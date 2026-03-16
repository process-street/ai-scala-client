package io.cequence.openaiscala.anthropic.service.impl

import org.apache.pekko.NotUsed
import play.api.libs.json.{JsValue, Json}
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.util.ByteString

object AwsEventStreamEventParser {
  def flow: Flow[ByteString, Option[JsValue], NotUsed] = Flow[ByteString].map { frame =>
    val rawString = new String(frame.toArray)

    if (rawString.contains("message-type")) {
      val jsonString = rawString.dropWhile(_ != '{').takeWhile(_ != '}') + "}"
      Some(Json.parse(jsonString))
    } else
      None
  }
}
