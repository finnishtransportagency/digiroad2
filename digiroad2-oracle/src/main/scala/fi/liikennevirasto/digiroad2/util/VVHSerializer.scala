package fi.liikennevirasto.digiroad2.util

import java.io.File

import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, TinyRoadLink}

/**
  * Created by venholat on 2.6.2016.
  */
trait VVHSerializer { //TODO DELETE
  def readCachedTinyRoadLinks(file: File): Seq[TinyRoadLink]

  def readCachedGeometry(file: File): Seq[RoadLink]

  def readCachedChanges(file: File): Seq[ChangeInfo]

  def writeCache(file: File, changes: Seq[Object]): Boolean
  
}
