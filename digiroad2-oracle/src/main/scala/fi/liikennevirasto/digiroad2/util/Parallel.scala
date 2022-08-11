package fi.liikennevirasto.digiroad2.util

import scala.collection.parallel.{ForkJoinTaskSupport, ParIterable}
import scala.concurrent.forkjoin.ForkJoinPool

class Parallel {
  private var parallelismLevel: Int = 1
  private var forkJoinPool: ForkJoinPool = null
  private def prepare[T](list: ParIterable[T]): ParIterable[T] = {
    forkJoinPool = new ForkJoinPool(parallelismLevel)
    list.tasksupport = new ForkJoinTaskSupport(forkJoinPool)
    list
  }
  def operation[T, U](list: ParIterable[T], parallelism: Int = 1)(f: ParIterable[T] => U):U = {
    parallelismLevel = parallelism
    val function = f(prepare[T](list))
    forkJoinPool.shutdown()
    function
  }
}
