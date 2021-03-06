package nest.sparkle.test

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import nest.sparkle.util.ConfigUtil
import nest.sparkle.util.LogUtil
import nest.sparkle.util.ConfigUtil.sparkleConfigName
import org.scalatest.Suite
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.file.Files
import java.nio.file.Paths
import scala.collection.JavaConverters._
import java.io.BufferedWriter
import java.io.PrintWriter
import nest.sparkle.util.Log

trait SparkleTestConfig extends Suite with BeforeAndAfterAll with Log{
  lazy val loggingInitialized = new AtomicBoolean

  override def beforeAll() {
    initializeLogging()
    super.beforeAll()
  }

  /** subclasses may override to modify the Config for particular tests */
  def configOverrides: List[(String, Any)] = List()

  /** subclasses may override to add a .conf file */
  def testConfigFile: Option[String] = Some("tests")

  /** return the outermost Config object. Also triggers logging initialization */
  lazy val rootConfig: Config = {
    val baseConfig = ConfigFactory.load()
    val root = testConfigFile match {
      case Some(confFile) => 
        val config = ConfigFactory.parseResources(confFile+".conf").resolve()
        val rendered = config.root.render
        config.withFallback(baseConfig)
      case None           => 
        baseConfig
    }
 
    val withOverrides = ConfigUtil.modifiedConfig(root, configOverrides: _*)

    ConfigUtil.dumpConfigToFile(withOverrides)
    initLogging(withOverrides)
    withOverrides
  }

  /** setup debug logging for tests (via sl4fj).  */
  def initializeLogging(): Unit = {
    rootConfig // reference the lazy val rootConfig to trigger it to evaluate 
  }

  /** setup logging for sparkle. Triggered automatically when the caller accesses
    * rootConfig. Idempotent.
    */
  private def initLogging(config: Config) {
    if (loggingInitialized.compareAndSet(false, true)) {
      LogUtil.configureLogging(config)
    } else {
      println("attempt to initialize logging twice!")
    }
  }

}
