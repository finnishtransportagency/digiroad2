package fi.liikennevirasto.digiroad2.client.kgv

import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.client.Filter

object Filter extends Filter {

 override def withFilter[T](attributeName: String, ids: Set[T]): String = {
  val filter =
   if (ids.isEmpty) {
    ""
   } else {
    val query = ids.mkString(",")
    s""""where":"$attributeName IN ($query)","""
   }
  filter
 }

 override def withMunicipalityFilter(municipalities: Set[Int]): String = {
  withFilter("MUNICIPALITYCODE", municipalities)
 }

 override def combineFiltersWithAnd(filter1: String, filter2: String): String = {

  (filter1.isEmpty, filter2.isEmpty) match {
   case (true,true) => ""
   case (true,false) => filter2
   case (false,true) => filter1
   case (false,false) => "%s AND %s".format(filter1.dropRight(2), filter2.replace("\"where\":\"", ""))
  }
 }

 override def combineFiltersWithAnd(filter1: String, filter2: Option[String]): String = {
  combineFiltersWithAnd(filter2.getOrElse(""), filter1)
 }

 /**
   *
   * @param polygon to be converted to string
   * @return string compatible with VVH polygon query
   */
 override def stringifyPolygonGeometry(polygon: Polygon): String = {
  var stringPolygonList: String = ""
  var polygonString: String = "{rings:[["
  polygon.getCoordinates
  if (polygon.getCoordinates.length > 0) {
   for (point <- polygon.getCoordinates.dropRight(1)) {
    // drop removes duplicates
    polygonString += "[" + point.x + "," + point.y + "],"
   }
   polygonString = polygonString.dropRight(1) + "]]}"
   stringPolygonList += polygonString
  }
  stringPolygonList
 }

 override def withMtkClassFilter(ids: Set[Long]): String = {
  withFilter("MTKCLASS", ids)
 }
}