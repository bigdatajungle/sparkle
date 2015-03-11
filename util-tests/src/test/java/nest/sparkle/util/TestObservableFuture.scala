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

package nest.sparkle.util

import rx.lang.scala.Observable

import nest.sparkle.util.ObservableFuture._
import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.Future
import scala.util.{Failure, Try}
import nest.sparkle.util.FutureAwait.Implicits._

class TestObservableFuture extends FunSuite with Matchers {

  import scala.concurrent.ExecutionContext.Implicits.global

  test("failed future to failed observable") {
    case class MyException() extends RuntimeException("ugh")

    val failed = Future.failed(MyException())
    val observable = failed.toObservable

    val tried = Try {observable.toBlocking.single}
    tried match {
      case Failure(MyException()) =>
      case x => fail(s"expected a failure with MyException, got: $x")
    }
  }

  test("future string to observable string") {
    val success = Future.successful("foo")
    val observable = success.toObservable

    observable.toBlocking.single shouldBe "foo"
  }

  test("empty observable to empty Seq") {
    Observable.empty.toFutureSeq.await shouldBe Seq()
  }

}
