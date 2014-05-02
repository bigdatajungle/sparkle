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

package nest.sparkle.time.protocol

import rx.lang.scala.Observable
import spray.json.JsonWriter
import spray.json._
import nest.sparkle.util.ObservableFuture._
import nest.sparkle.store.Event

/** returns an observable that produces one sequence of json arrays when the provided event stream completes */
object JsonEventWriter {
  /** returns an observable that produces one sequence of json arrays when the provided event stream completes */
  def fromObservable[T: JsonWriter, U: JsonWriter](events: Observable[Event[T, U]]): Observable[Seq[JsArray]] = {

    // LATER It would be nice to return all the available data here, but AFAICT the Observable api only gives
    // the choice of buffering by time or count, or getting all of the data.
    events.map{ eventToJsArray(_) }.toSeq
  }

  /** return an Observable containing sequence-chunks of json data from an Observable containing sequence-chunks
   *  of event data */
  def fromObservableSeq[T: JsonWriter, U: JsonWriter](observed: Observable[Seq[Event[T, U]]]): Observable[Seq[JsArray]] = {    
    observed.map { eventSeq =>
      eventSeq map { event => 
        eventToJsArray(event)
      }
    }
  }

  /** return the JsArray for one event */
  private def eventToJsArray[T: JsonWriter, U: JsonWriter](event: Event[T, U]): JsArray = {
    JsArray(event.argument.toJson, event.value.toJson)
  }

}

