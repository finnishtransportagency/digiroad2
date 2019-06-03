package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import fi.liikennevirasto.digiroad2.util.Track
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class RoadLinkTempDAO {
  def getByLinkIds(linkIds: Set[Long]): Seq[RoadAddressTEMP] = {
    val linkTypeInfo = MassQuery.withIds(linkIds) { idTableName =>
      sql"""select link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m, start_m_value, end_m_value, side_code
              from temp_road_address_info temp
              join  #$idTableName i on i.id = temp.link_id
         """.as[(Long, Option[Int], Long, Long, Int, Long, Long, Long, Long, Option[Int])].list
    }
    linkTypeInfo.map { case (linkId, municipalityCode, roadNumber, roadPart, trackCode, startAddressM, endAddressM, startMValue, endMValue, sideCode) =>
      RoadAddressTEMP(linkId, roadNumber, roadPart, Track.apply(trackCode), startAddressM, endAddressM, startMValue, endMValue, sideCode = sideCode.map(SideCode.apply), municipalityCode = municipalityCode)
    }
  }

  def getByRoadNumber(roadNumber: Int): Seq[RoadAddressTEMP] = {
    sql"""select link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m, start_m_value, end_m_value, side_code
            from temp_road_address_info where road_number = $roadNumber""".as[(Long, Option[Int], Long, Long, Int, Long, Long, Long, Long, Option[Int])].list
      .map { case (linkId, municipalityCode, roadNumber, roadPart, trackCode, startAddressM, endAddressM, startMValue, endMValue, sideCode) =>
        RoadAddressTEMP(linkId, roadNumber, roadPart, Track.apply(trackCode), startAddressM, endAddressM, startMValue, endMValue, sideCode = sideCode.map(SideCode.apply), municipalityCode = municipalityCode)
      }
  }

  def getIncomplete(): Seq[RoadAddressTEMP] = {
    sql"""select link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m, start_m_value, end_m_value, side_code
          from temp_road_address_info where track_code = ${Track.Unknown.value}""".as[(Long, Option[Int], Long, Long, Int, Long, Long, Long, Long, Option[Int])].list
      .map { case (linkId, municipalityCode, roadNumber, roadPart, trackCode, startAddressM, endAddressM, startMValue, endMValue, sideCode) =>
        RoadAddressTEMP(linkId, roadNumber, roadPart, Track.apply(trackCode), startAddressM, endAddressM, startMValue, endMValue, sideCode = sideCode.map(SideCode.apply), municipalityCode = municipalityCode)
      }
  }

  def insertInfo(roadAddressTemp: RoadAddressTEMP, username: String): Unit = {
    sqlu"""insert into temp_road_address_info (id, link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m, side_code  ,created_by)
             select primary_key_seq.nextval, ${roadAddressTemp.linkId}, ${roadAddressTemp.municipalityCode}, ${roadAddressTemp.road}, ${roadAddressTemp.roadPart}, ${roadAddressTemp.track.value}, ${roadAddressTemp.startAddressM}, ${roadAddressTemp.endAddressM},  ${roadAddressTemp.sideCode.map(_.value)}, $username
              from dual""".execute
  }

  def deleteInfoByMunicipality(municipalityCode: Int): Unit = {
    sqlu"""delete from temp_road_address_info
                 where municipality_code = $municipalityCode""".execute
  }
}