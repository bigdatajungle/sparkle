/* Copyright 2014  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.store.ram

import nest.sparkle.core.OngoingData
import nest.sparkle.datastream.DataArray
import nest.sparkle.store.Column
import scala.collection.immutable.VectorBuilder
import scala.reflect.runtime.universe._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import rx.lang.scala.Observable
import nest.sparkle.store.Event
import scala.collection.mutable.ArrayBuffer
import nest.sparkle.store.cassandra.WriteableColumn
import nest.sparkle.util.RecoverOrdering
import nest.sparkle.store.OngoingEvents
import nest.sparkle.measure.Span

/** A readable Column backed by a collection in the internal heap.  */
abstract class RamColumn[T: TypeTag, U: TypeTag](val name: String) extends Column[T, U] {
  def keys: Seq[T]
  def values: Seq[U]
  def keyType: TypeTag[T] = typeTag[T]
  def valueType: TypeTag[U] = typeTag[U]

  /** read a slice of events from the column, inclusive of the start and end values.
   *  If start is missing, read from the first element in the column.  If end is missing
   *  read from the last element in the column.  */  // SCALA just inherit description from trait?
  override def readRangeOld // format: OFF
      (start: Option[T] = None, end: Option[T] = None, limit:Option[Long], parentSpan:Option[Span]) 
      (implicit execution: ExecutionContext): OngoingEvents[T,U] = { // format: ON
    val (startDex, endDex) = keyRange(start, end)
    val results = keys.slice(startDex, endDex) zip values.slice(startDex, endDex)
    val events = results.map { case (key, value) => Event(key, value) }
    val result = Observable.from(events)

    OngoingEvents(result, Observable.empty)
  }

  override def readRange(
    start: Option[T] = None,
    end: Option[T] = None,
    limit: Option[Long],
    parentSpan: Option[Span]
  )(implicit execution: ExecutionContext): OngoingData[T, U] =
  {
    ???
  }

  /** Return the indices in the time array for the specified time bounds.
    * indices are inclusive of the first element index, and exclusive of the last index.
    * If the last index is 0, no elements are available (no index is before 0).
    */
  private def keyRange(start: Option[T], end: Option[T]): (Int, Int) = {
    val ordering = RecoverOrdering.ordering[T](keyType)
    val startDex: Int = {
      start map { startValue =>
        keys.indexWhere(ordering.gteq(_, startValue))
      } getOrElse 0
    }
    val afterEnd: Int = {
      end map { endValue =>
        keys.length - keys.reverseIterator.indexWhere(ordering.lt(_, endValue))
      } getOrElse keys.length
    }
    (startDex, afterEnd)
  }
}

/** Create a Writeable Column backed by an ArrayBuffer.  */
object WriteableRamColumn {
  def apply[T: TypeTag, U: TypeTag](name: String): WriteableRamColumn[T, U] =
    new WriteableRamColumn(name)
}

/** A Writeable Column backed by an ArrayBuffer.  */
class WriteableRamColumn[T: TypeTag, U: TypeTag](name: String)
    extends RamColumn[T, U](name) with WriteableColumn[T, U] {
  val keys = ArrayBuffer[T]()
  val values = ArrayBuffer[U]()

  /** Store some events in the internal column arrays.  Note not synchronized. */
  override def write(events: Iterable[Event[T, U]]) // format: OFF
      (implicit executionContext: ExecutionContext): Future[Unit] = { // format: ON
    for {
      Event(key, value) <- events
    } {
      keys.append(key)
      values.append(value)
    }
    Future.successful(())
  }

  /** NYI */
  override def writeData(dataArray:DataArray[T,U])
                    (implicit executionContext: ExecutionContext): Future[Unit] = {
    ???
  }


  /** does nothing, for compatibility with the WriteableColumn interface */
  def create(description: String)(implicit executionContext: ExecutionContext): Future[Unit] = {
    Future.successful(())
  }

  def erase()(implicit executionContext:ExecutionContext): Future[Unit] = {
    keys.clear()
    values.clear()
    Future.successful(())
  }
// SCALA TypedActor for this?
}

// TODO support notification in RamColumn, to get proper testing
