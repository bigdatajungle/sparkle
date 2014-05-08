package nest.sparkle.time.protocol

import scala.annotation.implicitNotFound
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import spray.json.{ JsObject, JsonWriter }
import spray.httpx.SprayJsonSupport._
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat
import spray.json.DefaultJsonProtocol._
import nest.sparkle.store.Column
import nest.sparkle.time.transform.CustomTransform
import com.typesafe.config.Config
import nest.sparkle.store.Event
import spray.json.JsonFormat
import nest.sparkle.util.RecoverJsonFormat

class DoublingTransform(rootConfig: Config) extends CustomTransform {
  override def name: String = this.getClass.getSimpleName

  override def apply[T, U]  // format: OFF
      (column: Column[T, U], transformParameters: JsObject)
      (implicit execution: ExecutionContext): JsonDataStream = { // format: ON
    val events = column.readRange(None, None)

    implicit val keyFormat = RecoverJsonFormat.jsonFormat[T](column.keyType)
    implicit val valueFormat = RecoverJsonFormat.jsonFormat[U](column.valueType)

    val doubled = events.map {
      case Event(key, value: Double) =>
        val double = (value * 2).asInstanceOf[U]
        Event(key, double)
    }
    JsonDataStream(
      dataStream = JsonEventWriter.fromObservable(doubled),
      streamType = KeyValueType
    )

  }
}
class TestCustomTransform extends TestStore with StreamRequestor with TestDataService {
  lazy val transformClassName = classOf[DoublingTransform].getCanonicalName
  override def configOverrides = {
    val transforms = Seq(s"$transformClassName").asJava
    super.configOverrides :+ "sparkle-time-server.custom-transforms" -> transforms
  }

  test("custom transform selector") {
    val requestMessage = streamRequest(classOf[DoublingTransform].getSimpleName)
    Post("/v1/data", requestMessage) ~> v1protocol ~> check {
      val events = streamDataEvents(response)
      events.length shouldBe 2
      events(0).value shouldBe 2
      events(1).value shouldBe 4
    }

  }

}