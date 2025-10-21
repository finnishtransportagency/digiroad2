/*
package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.Track
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.postgis.MassQuery
import fi.liikennevirasto.digiroad2.util.RoadAddressTEMP
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class RoadAddressTempDAO {
  def getByLinkIds(linkIds: Set[String]): Seq[RoadAddressTEMP] = {
    val linkTypeInfo = MassQuery.withStringIds(linkIds) { idTableName =>
      sql"""select link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m, start_m_value, end_m_value, side_code,to_char(created_date, 'YYYY-MM-DD HH:MI:SS.MS')
              from temp_road_address_info temp
              join  #$idTableName i on i.id = temp.link_id
         """.as[(String, Option[Int], Long, Long, Int, Long, Long, Long, Long, Option[Int], Option[String])].list
    }
    linkTypeInfo.map { case (linkId, municipalityCode, roadNumber, roadPart, trackCode, startAddressM, endAddressM, startMValue, endMValue, sideCode, createdDate) =>
      RoadAddressTEMP(linkId, roadNumber, roadPart, Track.apply(trackCode), startAddressM, endAddressM, startMValue, endMValue, sideCode = sideCode.map(SideCode.apply), municipalityCode = municipalityCode, createdDate = createdDate)
    }
  }

  def getByRoadNumber(roadNumber: Int): Seq[RoadAddressTEMP] = {
    sql"""select link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m, start_m_value, end_m_value, side_code, to_char(created_date, 'YYYY-MM-DD HH:MI:SS.MS')
            from temp_road_address_info where road_number = $roadNumber""".as[(String, Option[Int], Long, Long, Int, Long, Long, Long, Long, Option[Int], Option[String])].list
      .map { case (linkId, municipalityCode, roadNumber, roadPart, trackCode, startAddressM, endAddressM, startMValue, endMValue, sideCode, createdDate) =>
        RoadAddressTEMP(linkId, roadNumber, roadPart, Track.apply(trackCode), startAddressM, endAddressM, startMValue, endMValue, sideCode = sideCode.map(SideCode.apply), municipalityCode = municipalityCode, createdDate = createdDate)
      }
  }

  def getByMunicipality(municipality: Int): Seq[RoadAddressTEMP] = {
    sql"""select link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m, start_m_value, end_m_value, side_code, to_char(created_date, 'YYYY-MM-DD HH:MI:SS.MS')
            from temp_road_address_info where municipality_code = $municipality""".as[(String, Option[Int], Long, Long, Int, Long, Long, Long, Long, Option[Int], Option[String])].list
      .map { case (linkId, municipalityCode, roadNumber, roadPart, trackCode, startAddressM, endAddressM, startMValue, endMValue, sideCode, createdDate) =>
        RoadAddressTEMP(linkId, roadNumber, roadPart, Track.apply(trackCode), startAddressM, endAddressM, startMValue, endMValue, sideCode = sideCode.map(SideCode.apply), municipalityCode = municipalityCode, createdDate = createdDate)
      }
  }

  def insertInfo(roadAddressTemp: RoadAddressTEMP, username: String): Unit = {
    sqlu"""insert into temp_road_address_info (id, link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m, start_m_value, end_m_value, side_code  ,created_by)
           values (nextval('primary_key_seq'), ${roadAddressTemp.linkId},
           ${roadAddressTemp.municipalityCode}, ${roadAddressTemp.road},
           ${roadAddressTemp.roadPart}, ${roadAddressTemp.track.value},
           ${roadAddressTemp.startAddressM}, ${roadAddressTemp.endAddressM},
           ${roadAddressTemp.startMValue}, ${roadAddressTemp.endMValue},
           ${roadAddressTemp.sideCode.map(_.value)}, $username)""".execute
  }

  def deleteInfoByMunicipality(municipalityCode: Int): Unit = {
    sqlu"""delete from temp_road_address_info
           where municipality_code = $municipalityCode""".execute
  }

  def deleteInfoByLinkIds(linkIds: Set[String]): Unit = {
    val linkIdList = linkIds.map(id => s"'$id'").mkString(",")
    sqlu"""delete from temp_road_address_info
           where link_id in ($linkIdList)""".execute
  }
}*/
