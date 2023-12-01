package fi.liikennevirasto.digiroad2.util.CustomIterableOperations

import scala.collection.mutable

object IterableOperation {
  
  /**
    * 
    * @param list [[Iterable]] collection where [[T]] is element in list 
    * @param keyAccessor method which provide key, for example groupByPropertyHashMap(assetsAll, (elem: PersistedLinearAsset) => elem.linkId )
    * @tparam T object which is in list.
    * @tparam A is key which is field of [[T]]
    * @return `mutable.HashMap[A, Set[T]]`
    */
  def groupByPropertyHashMap[T, A](list: Iterable[T], keyAccessor: T => A): mutable.HashMap[A, Set[T]] = {
    val map = new mutable.HashMap[A, Set[T]]()
    for (elem <- list) {
      val elements = map.getOrElseUpdate(keyAccessor(elem), Set(elem))
      map.update(keyAccessor(elem), elements ++ Set(elem))
    }
    map
  }

}
