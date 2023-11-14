package fi.liikennevirasto.digiroad2.util.CustomIterableOperations

import fi.liikennevirasto.digiroad2.linearasset.PersistedLinearAsset

import scala.collection.immutable.HashMap
import scala.collection.mutable.ListBuffer
import scala.collection.parallel.immutable
import scala.collection.{Seq, TraversableLike, mutable}
import scala.collection.parallel.immutable

object IterableOperation {
  /**
    * 
    * @param list
    * @param getKey
    * @tparam T object which is in list.
    * @tparam A method which provide key, for example groupByPropertyHashMap(assetsAll, (elem: PersistedLinearAsset) => elem.linkId )
    * @return
    */
  def groupByPropertyHashMap[T, A](list: Iterable[T], getKey: T => A): mutable.HashMap[A, Set[T]] = {
    val map = new mutable.HashMap[A, Set[T]]()
    for (elem <- list) {
      val key = getKey(elem)
      val elements = map.getOrElseUpdate(key, Set(elem))
      map.update(key, elements ++ Set(elem))
    }
    map
  }

}
