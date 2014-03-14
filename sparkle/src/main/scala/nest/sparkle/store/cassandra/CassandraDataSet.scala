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

package nest.sparkle.store.cassandra

import scala.concurrent.{ ExecutionContext, Future }

import rx.lang.scala.Observable

import nest.sparkle.store.DataSet
import nest.sparkle.store.Column

case class CassandraDataSet(store: CassandraStore, name: String) extends DataSet {
  implicit def execution: ExecutionContext = store.execution
  
  /** return a column in this dataset (or FileNotFound) */
  def column[T, U](columnName: String): Future[Column[T, U]] = {
    ???
  }

  /** return all child columns */
  def childColumns: Observable[String] = {
    val read = store.dataSetCatalog.childrenOfParentPath(name)
    read.filter { _.isColumn }.
      map { entry => entry.childPath }
  }

  /** return all child datasets */
  def childDataSets: Observable[DataSet] = {
    val read = store.dataSetCatalog.childrenOfParentPath(name)
    read.filter { ! _.isColumn }.
      map { entry => CassandraDataSet(store, entry.childPath) }
  }
}
