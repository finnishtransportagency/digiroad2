package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.client.ReplaceInfoWithGeometry
import fi.liikennevirasto.digiroad2.postgis.MassQuery

case class ReplaceInfoRow(id: Long, oldLinkId: Option[String], oldGeometry: List[Point], newLinkId: Option[String],
                          newGeometry: List[Point], oldFromMValue: Option[Double], oldToMValue: Option[Double],
                          newFromMValue: Option[Double], newToMValue: Option[Double], digitizationChange: Boolean)
class RoadLinkMissingReplacementDAO {

  def findPotentialMatches(): Unit = {

  }

  def insertReplaceInfos(replaceInfos: Seq[ReplaceInfoWithGeometry]) = {
    val ids = Sequences.nextPrimaryKeySeqValues(replaceInfos.size)
    val replaceInfosWithIds = replaceInfos.zipWithIndex.map { case (replaceInfo, index) =>
      ReplaceInfoRow(ids(index), replaceInfo.oldLinkId, replaceInfo.oldGeometry, replaceInfo.newLinkId, replaceInfo.newGeometry,
        replaceInfo.oldFromMValue, replaceInfo.oldToMValue, replaceInfo.newFromMValue, replaceInfo.newToMValue, replaceInfo.digitizationChange)
    }

    val insertReplaceInfoRow =
      s"""insert into replace_info (id, old_link_id, old_geometry, new_link_id, new_geometry, old_from_m_value,
         | old_to_m_value, new_from_m_value, new_to_m_value, digitization_change)
         |values ((?), (?), (?), (?), (?), (?), (?), (?), (?), (?))""".stripMargin
    MassQuery.executeBatch(insertReplaceInfoRow) { statement =>
      replaceInfosWithIds.foreach { ri =>
        statement.setLong(1, ri.id)
        if(ri.oldLinkId.nonEmpty) statement.setString(2, ri.oldLinkId.get)
        else statement.setNull(2, java.sql.Types.VARCHAR)
        if(ri.oldGeometry.nonEmpty) {
          val geomWKT = GeometryUtils.toWktLineString(ri.oldGeometry).string
          statement.setObject(3, geomWKT, java.sql.Types.OTHER)
        } else statement.setNull(3, java.sql.Types.OTHER)
        if(ri.newLinkId.nonEmpty) statement.setString(4, ri.newLinkId.get)
        else statement.setNull(4, java.sql.Types.VARCHAR)
        if(ri.newGeometry.nonEmpty) {
          val geomWKT = GeometryUtils.toWktLineString(ri.newGeometry).string
          statement.setObject(5, geomWKT, java.sql.Types.OTHER)
        } else statement.setNull(5, java.sql.Types.OTHER)
        if(ri.oldFromMValue.nonEmpty) statement.setDouble(6, ri.oldFromMValue.get)
        else statement.setNull(6, java.sql.Types.DOUBLE)
        if(ri.oldToMValue.nonEmpty) statement.setDouble(7, ri.oldToMValue.get)
        else statement.setNull(7, java.sql.Types.DOUBLE)
        if(ri.newFromMValue.nonEmpty) statement.setDouble(8, ri.newFromMValue.get)
        else statement.setNull(8, java.sql.Types.DOUBLE)
        if(ri.newToMValue.nonEmpty) statement.setDouble(9, ri.newToMValue.get)
        else statement.setNull(9, java.sql.Types.DOUBLE)
        statement.setBoolean(10, ri.digitizationChange)
        statement.addBatch()
      }
    }
  }
}
