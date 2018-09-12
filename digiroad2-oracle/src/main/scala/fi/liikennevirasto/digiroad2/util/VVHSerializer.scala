package fi.liikennevirasto.digiroad2.util

import java.io.File

import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHRoadNodes}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, TinyRoadLink}

/**
  * Created by venholat on 2.6.2016.
  */
trait VVHSerializer {
  def readCachedTinyRoadLinks(file: File): Seq[TinyRoadLink]

  def readCachedGeometry(file: File): Seq[RoadLink]

  def readCachedChanges(file: File): Seq[ChangeInfo]

  def writeCache(file: File, changes: Seq[Object]): Boolean

  def readCachedNodes(file: File): Seq[VVHRoadNodes]
}
