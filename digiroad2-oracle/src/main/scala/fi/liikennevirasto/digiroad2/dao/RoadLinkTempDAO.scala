package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import fi.liikennevirasto.digiroad2.util.Track
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class RoadLinkTempDAO {
  def getByLinkIds(linkIds: Set[Long]): Seq[RoadAddressTEMP] = {
    val linkTypeInfo = MassQuery.withIds(linkIds) { idTableName =>
      sql"""select link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m, start_m_value, end_m_value, side_code, created_date
              from temp_road_address_info temp
              join  #$idTableName i on i.id = temp.link_id
         """.as[(Long, Option[Int], Long, Long, Int, Long, Long, Long, Long, Option[Int], Option[String])].list
    }
    linkTypeInfo.map { case (linkId, municipalityCode, roadNumber, roadPart, trackCode, startAddressM, endAddressM, startMValue, endMValue, sideCode, createdDate) =>
      RoadAddressTEMP(linkId, roadNumber, roadPart, Track.apply(trackCode), startAddressM, endAddressM, startMValue, endMValue, sideCode = sideCode.map(SideCode.apply), municipalityCode = municipalityCode, createdDate = createdDate)
    }
  }

  def getByRoadNumber(roadNumber: Int): Seq[RoadAddressTEMP] = {
    sql"""select link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m, start_m_value, end_m_value, side_code, created_date
            from temp_road_address_info where road_number = $roadNumber""".as[(Long, Option[Int], Long, Long, Int, Long, Long, Long, Long, Option[Int], Option[String])].list
      .map { case (linkId, municipalityCode, roadNumber, roadPart, trackCode, startAddressM, endAddressM, startMValue, endMValue, sideCode, createdDate) =>
        RoadAddressTEMP(linkId, roadNumber, roadPart, Track.apply(trackCode), startAddressM, endAddressM, startMValue, endMValue, sideCode = sideCode.map(SideCode.apply), municipalityCode = municipalityCode, createdDate = createdDate)
      }
  }

  def getByMunicipality(municipality: Int): Seq[RoadAddressTEMP] = {
    sql"""select link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m, start_m_value, end_m_value, side_code, created_date
            from temp_road_address_info where municipality_code = $municipality""".as[(Long, Option[Int], Long, Long, Int, Long, Long, Long, Long, Option[Int], Option[String])].list
      .map { case (linkId, municipalityCode, roadNumber, roadPart, trackCode, startAddressM, endAddressM, startMValue, endMValue, sideCode, createdDate) =>
        RoadAddressTEMP(linkId, roadNumber, roadPart, Track.apply(trackCode), startAddressM, endAddressM, startMValue, endMValue, sideCode = sideCode.map(SideCode.apply), municipalityCode = municipalityCode, createdDate = createdDate)
      }
  }

  def getByRoadNumberRoadPartTrack(roadNumber: Int, trackCode: Int, roadPart: Set[Long]): Seq[RoadAddressTEMP] = {
    val linkTypeInfo = MassQuery.withIds(roadPart) { idTableName =>
      sql"""SELECT link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m, start_m_value, end_m_value, side_code, created_date
            FROM temp_road_address_info t
             JOIN #$idTableName i on i.id = t.road_part
            WHERE road_number = $roadNumber
            AND track_code = $trackCode
            """.as[(Long, Option[Int], Long, Long, Int, Long, Long, Long, Long, Option[Int], Option[String])].list
        }
      linkTypeInfo.map { case (linkId, municipalityCode, roadNumber, roadPart, trackCode, startAddressM, endAddressM, startMValue, endMValue, sideCode, createdDate) =>
        RoadAddressTEMP(linkId, roadNumber, roadPart, Track.apply(trackCode), startAddressM, endAddressM, startMValue, endMValue, sideCode = sideCode.map(SideCode.apply), municipalityCode = municipalityCode, createdDate = createdDate)
    }
  }


  def insertInfo(roadAddressTemp: RoadAddressTEMP, username: String): Unit = {
    sqlu"""insert into temp_road_address_info (id, link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m, start_m_value, end_m_value, side_code  ,created_by)
             select primary_key_seq.nextval, ${roadAddressTemp.linkId}, ${roadAddressTemp.municipalityCode}, ${roadAddressTemp.road}, ${roadAddressTemp.roadPart}, ${roadAddressTemp.track.value},
      ${roadAddressTemp.startAddressM}, ${roadAddressTemp.endAddressM}, ${roadAddressTemp.startMValue}, ${roadAddressTemp.endMValue}, ${roadAddressTemp.sideCode.map(_.value)}, $username
              from dual""".execute
  }

  def deleteInfoByMunicipality(municipalityCode: Int): Unit = {
    sqlu"""delete from temp_road_address_info
           where municipality_code = $municipalityCode""".execute
  }

  def deleteInfoByLinkIds(linkIds: Set[Long]): Unit = {
    if (linkIds.nonEmpty) linkIds.mkString(",") else ""
    sqlu"""delete from temp_road_address_info
           where link_id in (#${linkIds.mkString(",")})""".execute
  }
}