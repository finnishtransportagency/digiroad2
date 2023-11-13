package fi.liikennevirasto.digiroad2.util.CustomIterableOperations

import fi.liikennevirasto.digiroad2.linearasset.PersistedLinearAsset

import scala.collection.immutable.HashMap
import scala.collection.mutable.ListBuffer
import scala.collection.parallel.immutable
import scala.collection.{Seq, TraversableLike, mutable}
import scala.collection.parallel.immutable

object IterableOperation {
  //TODO generalise when there are three more different list types
  def groupByHashMap(assetsAll: Seq[PersistedLinearAsset]): mutable.HashMap[String, Set[PersistedLinearAsset]] = {
    val map = new mutable.HashMap[String, Set[PersistedLinearAsset]]()
    for (elem <- assetsAll) { // for loop is often faster than map
      val key = elem.linkId
      val elements = map.getOrElseUpdate(key, Set(elem)) //insert if first elements
      map.update(key, elements ++ Set(elem)) // if not first element merge element which are already in list and update list
    } // test that this works
    map
  }

}
