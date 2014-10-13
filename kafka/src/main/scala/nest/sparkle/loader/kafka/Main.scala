package nest.sparkle.loader.kafka

import scala.reflect.runtime.universe._
import scala.concurrent.ExecutionContext.Implicits.global

import org.clapper.argot.ArgotConverters._

import nest.sparkle.store.Store
import nest.sparkle.store.cassandra.WriteNotification  // TODO: shouldn't require cassandra
import nest.sparkle.util.{Log, InitializeReflection, SparkleApp}
import nest.sparkle.util.ConfigUtil.sparkleConfigName

/** main() entry point to launch a kafka loader.  Pass a command line argument (--conf) to
  * point to the config file to use.  The server will launch with default configuration otherwise.
  */
trait Main 
  extends SparkleApp 
          with Log 
{
  val erase = parser.flag[Boolean](List("format"), "erase and format the database")
  
  initialize()
  InitializeReflection.init
  
  val loader = {
    val notification = new WriteNotification()
    val writeableStore = Store.instantiateWritableStore(sparkleConfig, notification)
    val storeKeyType = typeTag[Long]
    new AvroKafkaLoader(rootConfig, writeableStore)(storeKeyType, global)
  }
  
  loader.start()
  log.info("Kafka loaders started")
  
  @volatile
  var keepRunning = true
  
  // handle SIGTERM which upstart uses to terminate a service
  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run(): Unit = {
      loader.shutdown()
      keepRunning = false
    }
  })
  
  // FUTURE: write status info to the log
  while (keepRunning) {
    Thread.sleep(15 * 60 * 1000L)
    log.trace("alive")
  }
  
  log.info("Main loader thread terminating")
  
  override def overrides = {
    erase.value.map { (s"$sparkleConfigName.erase-store", _) }.toSeq
  }
}
