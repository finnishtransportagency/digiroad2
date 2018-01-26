package fi.liikennevirasto.viite.dao

import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.dao.Sequences
import fi.liikennevirasto.viite.NewLrmPosition
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

case class LRMPosition(id: Long, sideCode: SideCode, startMeasure: Double, endMeasure: Double, linkId: Long,
                       adjustedTimestamp: Long, linkSource: LinkGeomSource) {

}

object LRMPositionDAO {

  /**
    * Persist LRMPosition in LRM_POSITION table.
    *
    * If id <= 0, LRMPosition is inserted
    * If id > 0, LRMPosition is updated
    *
    * @param lrmPosition
    * @return LRMPosition (with the updated id in the case of insert)
    */
  def save(lrmPosition: LRMPosition): LRMPosition = {
    if (lrmPosition.id <= 0) {
      insert(lrmPosition)
    } else {
      update(lrmPosition)
    }
  }

  private def insert(lrmPosition: LRMPosition): LRMPosition = {
    val id = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
          insert into lrm_position (
                  id, link_id, side_code, start_measure,
                  end_measure, adjusted_timestamp, link_source)
          values (${id}, ${lrmPosition.linkId}, ${lrmPosition.sideCode.value}, ${lrmPosition.startMeasure},
                  ${lrmPosition.endMeasure}, ${lrmPosition.adjustedTimestamp}, ${lrmPosition.linkSource.value})
      """.execute
    lrmPosition.copy(id = id)
  }

  private def update(lrmPosition: LRMPosition): LRMPosition = {
    sqlu"""
          update lrm_position set link_id = ${lrmPosition.linkId}, SIDE_CODE = ${lrmPosition.sideCode.value},
                  start_measure = ${lrmPosition.startMeasure}, end_measure = ${lrmPosition.endMeasure},
                  adjusted_timestamp = ${lrmPosition.adjustedTimestamp}, link_source = ${lrmPosition.linkSource.value}
          where id = ${lrmPosition.id}
      """.execute
    lrmPosition
  }

}
