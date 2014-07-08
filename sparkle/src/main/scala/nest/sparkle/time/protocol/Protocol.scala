/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.time.protocol

import spray.json._
import nest.sparkle.util.LogUtil.optionLog

/** spray json converters for json objects we send or receive */

/** message wrapper.  to or from the server */
trait DistributionMessage[T] {
  val requestId: Option[Long]
  val realm: Option[Realm]
  val traceId: Option[String]
  val messageType: String
  val message: T
}

/** distribution layer message type */
object MessageType {
  val StreamRequest = "StreamRequest"
  val Streams = "Streams"
  val Status = "Status"
  val Update = "Update"
}

/** realm specifier in a Distribution Message */
case class Realm(name: String, id:Option[String], auth:Option[String])
// TODO distinguish between upstream and downstream Realm, downstream Realm cannot have id and auth

/** request a transformed stream.  sent from from client to server */
case class StreamRequest(sendUpdates: Option[Boolean], itemLimit: Option[Long], sources: Array[JsValue],
                         transform: String, transformParameters: JsObject) {
  def infoString = this.toJson(RequestJson.StreamRequestFormat).compactPrint
}

/** a StreamRequest inside a DistributionMessage */
case class StreamRequestMessage(requestId: Option[Long], realm: Option[Realm], traceId: Option[String],
                                messageType: String, message: StreamRequest) extends DistributionMessage[StreamRequest] {
  def infoString = this.toJson(RequestJson.StreamRequestMessageFormat).compactPrint
}

/** a Stream inside a Distribution Message */
case class StreamsMessage(requestId: Option[Long], realm: Option[Realm], traceId: Option[String],
                          messageType: String, message: Streams) extends DistributionMessage[Streams] {

  /** (for debug logging) return a copy of the StreamsMessage with a maximum data size for each
    * stream's data.
    */
  def takeData(maxDataSize: Int): StreamsMessage = {
    val sizeLimitedStreams = message.streams.map{ orig =>
      orig.copy(data = orig.data.map(_.take(maxDataSize)))
    }
    val sizeLimitedMessage = message.copy(streams = sizeLimitedStreams)
    val copy = this.copy(message = sizeLimitedMessage)
    copy
  }
}

// TODO find a way to do less copy-pasting on encoding various message types
case class StatusMessage(requestId: Option[Long], realm: Option[Realm], traceId: Option[String],
                         messageType: String, message: Status)

case class UpdateMessage(requestId: Option[Long], realm: Option[Realm], traceId: Option[String],
                         messageType: String, message: Update) {
  /** (for debug logging) return a copy of the UpdateMessage with a maximum data size for each
    * stream's data.
    */
  def takeData(maxDataSize: Int): UpdateMessage = {
    val smallerData = message.data.map { data => data.take(maxDataSize) }
    val sizeLimitedUpdate = message.copy(data = smallerData)
    this.copy(message = sizeLimitedUpdate)
  }
}

/** Limit the flow of data over a stream temporarily.  Sent from client to Server */
case class StreamControl(streamId: Long, maxItems: Long)

/** Stop a stream.  Sent from client to server.*/
case class StreamDrop(streamId: Long)

/** Report errors and other status events.  Sent from server to client. */
case class Status(code: Long, description: String)

/** an element in the sources array of a StreamRequest message that specifies a custom source selector */
case class CustomSelector(selector: String, selectorParameters: JsObject)

/** transformParameters element for transforms that specify a range of data to load (e.g. SummaryTransforms) */
case class RangeInterval[T](
  start: Option[T] = None,
  until: Option[T] = None,
  limit: Option[Long] = None)

/** transformParameters for SummaryTransforms */
case class SummaryParameters[T](
  ranges: Option[Seq[RangeInterval[T]]] = None,
  maxPartitions: Option[Int] // TODO rename to parts or partCount 
                             // TODO consider allowing partSize
  )
  
case class IntervalParameters[T](
  ranges: Option[Seq[RangeInterval[T]]] = None,
  partSize: Option[String]
  )
    

/** transformParameters for the RawTransform */
case class RawParameters[T](ranges: Option[Seq[RangeInterval[T]]] = None)

sealed trait ServerResponse
/** Start some data streams.  Sent from server to client */
case class Streams(streams: Seq[Stream]) extends ServerResponse
case class Update(streamId: Long, data: Option[Seq[JsArray]], end: Option[Boolean]) extends ServerResponse

/** Description of the head of a data stream.  Sent from server to client as part of a Streams message.  */
case class Stream(streamId: Long, metadata: Option[JsValue], streamType: JsonStreamType,
                  data: Option[Seq[JsArray]], end: Option[Boolean])

/** Type of the json data stream: keyValue or just value */
abstract class JsonStreamType(val name: String)
case object KeyValueType extends JsonStreamType("KeyValue")
case object ValueType extends JsonStreamType("Value")




