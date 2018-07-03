package fi.liikennevirasto.digiroad2.util

import java.io.File

import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHRoadNodes}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, TinnyRoadLink}

/**
  * Created by venholat on 2.6.2016.
  */
trait VVHSerializer {
  def readCachedTinnyRoadLinks(file: File): Seq[TinnyRoadLink]

  def readCachedGeometry(file: File): Seq[RoadLink]

  def readCachedChanges(file: File): Seq[ChangeInfo]

  def writeCache(file: File, changes: Seq[Object]): Boolean

  def readCachedNodes(file: File): Seq[VVHRoadNodes]
}
