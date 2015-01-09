package nest.sparkle.time.transform


import scala.reflect.runtime.universe._
import nest.sparkle.time.protocol.RangeInterval
import scala.concurrent.ExecutionContext
import rx.lang.scala.Observable
import scala.concurrent.Future
import nest.sparkle.core.OngoingData
import nest.sparkle.core.DataArray
import scala.{ specialized => spec }
import nest.sparkle.util.ReflectionUtil

/** A TwoPartStream containing data in the form it comes off from the database. Initial request data
  * is asynchronously delivered, block at a time, as it returned from the database driver.
  * The TwoPartStream bundles the range interval request that produced this data for further
  * downstream processing.
  */
case class AsyncWithRange[K: TypeTag, V: TypeTag] // format: OFF
    ( initial: DataStream[K,V], 
      ongoing: DataStream[K,V],
      requestRange: Option[RangeInterval[K]] ) 
    extends TwoPartStream[K,V,AsyncWithRange] 
      with RequestRange[K] with AsyncReduction[K,V] { // format: ON

  def mapData[B: TypeTag] // format: OFF
      (fn: DataArray[K,V] => DataArray[K,B])
      (implicit execution:ExecutionContext)
      : AsyncWithRange[K,B] = { // format: ON
    val newInitial = DataStream(initial.data.map(fn))
    val newOngoing = DataStream(ongoing.data.map(fn))
    AsyncWithRange(newInitial, newOngoing, requestRange)
  }

  override def keyType = typeTag[K]
  override def valueType = typeTag[V]

  implicit lazy val keyClassTag = ReflectionUtil.classTag[K](keyType)
  implicit lazy val valueClassTag = ReflectionUtil.classTag[V](valueType)

  override def mapInitial[A](fn: DataArray[K, V] => A): Observable[A] = initial.data map fn
  override def mapOngoing[A](fn: DataArray[K, V] => A): Observable[A] = ongoing.data map fn

  override def doOnEach(fn: DataArray[K, V] => Unit): AsyncWithRange[K, V] = {
    copy(
      initial = DataStream(initial.data doOnEach fn),
      ongoing = DataStream(ongoing.data doOnEach fn)
    )
  }

  override def plus(other: TwoPartStream[K, V, AsyncWithRange]) // format: OFF
    : TwoPartStream[K, V, AsyncWithRange] = { // format: ON
    AsyncWithRange(
      initial = DataStream(initial.data ++ other.self.initial.data),
      ongoing = DataStream(ongoing.data ++ other.self.ongoing.data),
      requestRange = requestRange
    )
  }
  
}

object AsyncWithRange {
  /** convenience constructor for creating an instance from an OngoingData */
  def apply[K: TypeTag, V: TypeTag] // format: OFF
    (ongoingData: OngoingData[K, V], requestRange: Option[RangeInterval[K]])
    : AsyncWithRange[K, V] = { // format: ON
    (new AsyncWithRange(
        initial = DataStream(ongoingData.initial), 
        ongoing = DataStream(ongoingData.ongoing), 
        requestRange = requestRange)(ongoingData.keyType, ongoingData.valueType))
  }

  def error[K: TypeTag, V: TypeTag] // format: OFF
      ( err: Throwable, requestRange: Option[RangeInterval[K]] )
      : AsyncWithRange[K, V] = { // format: ON
    AsyncWithRange[K, V](
        initial = DataStream[K,V](Observable.error(err)), 
        ongoing = DataStream[K,V](Observable.error(err)),
        requestRange = requestRange)
  }
}
