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

package nest.sparkle.store.cassandra

import com.datastax.driver.core.Session
import nest.sparkle.store.Column
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import rx.lang.scala.Observable
import nest.sparkle.store.Event
import com.datastax.driver.core.Row
import com.datastax.driver.core.PreparedStatement
import nest.sparkle.store.cassandra.ObservableResultSet._
import scala.reflect.runtime.universe._
import nest.sparkle.util._
import nest.sparkle.util.Log

import SparseColumnReader.{ log, statement, ReadAll, ReadRange }
import com.datastax.driver.core.BoundStatement

object SparseColumnReader extends PrepareTableOperations with Log {
  case class ReadAll(override val tableName: String) extends TableOperation
  case class ReadRange(override val tableName: String) extends TableOperation

  def instance[T, U](dataSetName: String, columnName: String, catalog: ColumnCatalog) // format: OFF
      (implicit session: Session, execution: ExecutionContext): Future[SparseColumnReader[T,U]] = { // format: ON

    val columnPath = ColumnSupport.constructColumnPath(dataSetName, columnName)

    /** create a SparseColumnReader of the supplied X,Y type. The cast it to the
      * (presumably _) type of instance()s T,U parameters.
      */
    def makeReader[X, Y](key: CanSerialize[X], value: CanSerialize[Y]): SparseColumnReader[T, U] = {
      val typed = new SparseColumnReader(dataSetName, columnName, catalog)(key, value, session)
      typed.asInstanceOf[SparseColumnReader[T, U]]
    }

    /** create a reader of the appropriate type */
    def reader(catalogInfo: CatalogInfo): SparseColumnReader[T, U] = {
      catalogInfo match {
        case LongDoubleSerializers(KeyValueSerializers(key, value))  => makeReader(key, value)
        case LongLongSerializers(KeyValueSerializers(key, value))    => makeReader(key, value)
        case LongIntSerializers(KeyValueSerializers(key, value))     => makeReader(key, value)
        case LongBooleanSerializers(KeyValueSerializers(key, value)) => makeReader(key, value)
        case LongStringSerializers(KeyValueSerializers(key, value))  => makeReader(key, value)
        case _                                                       => ???
      }
    }

    for {
      catalogInfo <- catalog.catalogInfo(columnPath)
    } yield {
      reader(catalogInfo)
    }
  }

  override val prepareStatements =
    (ReadAll -> readAllStatement _) ::
      (ReadRange -> readRangeStatement _) ::
      Nil

  def readAllStatement(tableName: String): String = s"""
      SELECT argument, value FROM $tableName
      WHERE dataSet = ? AND column = ? AND rowIndex = ?
      """

  def readRangeStatement(tableName: String): String = s"""
      SELECT argument, value FROM $tableName
      WHERE dataSet = ? AND column = ? AND rowIndex = ? AND argument >= ? AND argument < ?
      """
}

/** read only access to a cassandra source event column */
class SparseColumnReader[T: CanSerialize, U: CanSerialize](val dataSetName: String, // format: OFF
      val columnName: String, catalog: ColumnCatalog) (implicit val session: Session)
      extends Column[T, U] with ColumnSupport { // format: ON

  def name: String = columnName

  private val keySerializer = implicitly[CanSerialize[T]]
  private val valueSerializer = implicitly[CanSerialize[U]]
  def keyType: TypeTag[T] = keySerializer.typedTag
  def valueType: TypeTag[U] = valueSerializer.typedTag

  val serialInfo = ColumnTypes.serializationInfo()(keySerializer, valueSerializer)
  val tableName = serialInfo.tableName

  /** read a slice of events from the column */      // format: OFF
  def readRange(start:Option[T] = None, end:Option[T] = None)
      (implicit execution: ExecutionContext): Observable[Event[T,U]] = { // format: ON
    (start, end) match {
      case (None, None)             => readAll()
      case (Some(start), Some(end)) => readRange(start, end)
      case _                        => ???
    }
  }

  /** read a range of events from the column */      // format: OFF
  def readBefore(start:T, maxResults:Long = Long.MaxValue)
      (implicit execution: ExecutionContext): Observable[Event[T,U]] = { // format: ON
    ???
  }

  /** read a range of events from the column */      // format: OFF
  def readAfter(start:T, maxResults:Long = Long.MaxValue)
      (implicit execution: ExecutionContext): Observable[Event[T,U]] = { // format: ON
    ???
  }

  /** read all the column values from the column */
  private def readAll()(implicit executionContext: ExecutionContext): Observable[Event[T, U]] = { // format: ON
    log.trace(s"readAll from $tableName $columnPath")
    val readStatement = statement(ReadAll(tableName)).bind(
      Seq[AnyRef](dataSetName, columnName, rowIndex): _*)

    readEventRows(readStatement)
  }

  def rowDecoder(row: Row): Event[T, U] = {
    log.trace(s"rowDecoder: $row")
    val argument = keySerializer.fromRow(row, 0)
    val value = valueSerializer.fromRow(row, 1)
    Event(argument.asInstanceOf[T], value.asInstanceOf[U])
  }

  private def readEventRows(statement: BoundStatement) // format: OFF
      (implicit execution:ExecutionContext): Observable[Event[T, U]] = { // format: ON
    val rows = session.executeAsync(statement).observerableRows
    rows map rowDecoder
  }

  private def readRange(start: T, end: T) // format: OFF
      (implicit execution:ExecutionContext): Observable[Event[T, U]] = { // format: ON
    log.trace(s"readRange from $tableName $columnPath $start $end")
    val readStatement = statement(ReadRange(tableName)).bind(
      Seq[AnyRef](dataSetName, columnName, rowIndex,
        start.asInstanceOf[AnyRef], end.asInstanceOf[AnyRef]): _*)

    readEventRows(readStatement)
  }
}
