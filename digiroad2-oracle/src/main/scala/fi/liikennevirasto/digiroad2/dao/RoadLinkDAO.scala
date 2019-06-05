package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import fi.liikennevirasto.digiroad2.service.LinkProperties
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

sealed trait RoadLinkDAO{

  def table: String
  def column : String

  def getValue(linkProperty: LinkProperties) : Int

  def getVVHValue(vvhRoadLink: VVHRoadlink): Option[Int]

  def getExistingValue(linkId: Long): Option[Int]= {
    sql"""select #$column from #$table where link_id = $linkId""".as[Int].firstOption
  }


  def insertValues(linkProperty: LinkProperties, vvhRoadlink: VVHRoadlink, username: Option[String], value: Int, mmlId: Option[Long]): Unit = {
    insertValues(linkProperty, username, value)
  }

  def insertValues(linkProperty: LinkProperties, username: Option[String], value: Int): Unit = {
    sqlu"""insert into #$table (id, link_id, #$column, modified_by )
                   select primary_key_seq.nextval, ${linkProperty.linkId}, $value, $username
                   from dual
                   where not exists (select * from #$table where link_id =${linkProperty.linkId})""".execute
  }


  def insertValues(linkId: Long, username: Option[String], value: Int) = {
    sqlu"""insert into #$table (id, link_id, #$column, modified_by )
                   select primary_key_seq.nextval, $linkId, $value, $username
                   from dual
                   where not exists (select * from #$table where link_id = $linkId)""".execute
  }

  def insertValues(linkId: Long, username: Option[String], value: Int, timeStamp: String) = {
    sqlu"""insert into #$table (id, link_id, #$column, modified_date, modified_by)
                 select primary_key_seq.nextval, ${linkId}, $value,
                 to_timestamp_tz($timeStamp, 'YYYY-MM-DD"T"HH24:MI:SS.ff3"+"TZH:TZM'), $username
                 from dual
                 where not exists (select * from #$table where link_id = $linkId)""".execute
  }


  def updateValues(linkProperty: LinkProperties, vvhRoadlink: VVHRoadlink, username: Option[String], value: Int, mml_id: Option[Long]): Unit = {
      sqlu"""update #$table
               set #$column = $value,
                   modified_date = SYSDATE,
                   modified_by = $username
               where link_id = ${linkProperty.linkId}""".execute
  }

  def updateValues(linkProperty: LinkProperties, username: Option[String], value: Int): Unit = {
    sqlu"""update #$table
               set #$column = $value,
                   modified_date = SYSDATE,
                   modified_by = $username
               where link_id = ${linkProperty.linkId}""".execute
  }

  def updateValues(linkId: Long, username: Option[String], value: Int): Unit = {
    sqlu"""update #$table
               set #$column = $value,
                   modified_date = SYSDATE,
                   modified_by = $username
               where link_id = $linkId""".execute
  }


  def expireValues(linkId: Long, username: Option[String]) = {
    sqlu"""update #$table
                 set valid_to = SYSDATE - 1,
                     modified_by = $username
                 where link_id = $linkId""".execute
  }

  def deleteValues(linkId: Long) = {
    sqlu"""delete from #$table
                 where link_id = $linkId""".execute
  }
}


object RoadLinkDAO{

  val FunctionalClass = "functional_class"
  val TrafficDirection = "traffic_direction"
  val LinkType = "link_type"
  val AdministrativeClass = "administrative_class"
  val VVHAdministrativeClass = "vvh_administrative_class"
  val LinkAttributes = "road_link_attributes"

  private def getDao(propertyName: String): RoadLinkDAO = {
    propertyName.toLowerCase match {
      case FunctionalClass => FunctionalClassDao
      case TrafficDirection => TrafficDirectionDao
      case LinkType => LinkTypeDao
      case AdministrativeClass => AdministrativeClassDao
      case LinkAttributes => LinkAttributesDao
    }
  }

  def get(propertyName: String, linkId: Long): Option[Int]= {
    val dao = getDao(propertyName)
    dao.getExistingValue(linkId)
  }

  def getValue(propertyName: String, linkProperty: LinkProperties): Int  = {
    val dao = getDao(propertyName)
    dao.getValue(linkProperty)
  }

  def getVVHValue(propertyName: String, vvhRoadLink: VVHRoadlink): Option[Int] = {
    val dao = getDao(propertyName)
    dao.getVVHValue(vvhRoadLink)
  }


  def insert(propertyName: String, linkProperty: LinkProperties, vvhRoadLink: VVHRoadlink, username: Option[String], mmlId: Option[Long]) = {
    val dao = getDao(propertyName)
    val value = dao.getValue(linkProperty)
    dao.insertValues(linkProperty, vvhRoadLink, username, value, mmlId)
  }

  def insert(propertyName: String, linkId: Long, username: Option[String], value: Int) = {
    val dao = getDao(propertyName)
    dao.insertValues(linkId, username, value)
  }

  def insert(propertyName: String, linkProperty: LinkProperties, username: Option[String], timeStamp: String) = {
    val dao = getDao(propertyName)
    val value = dao.getValue(linkProperty)
    dao.insertValues(linkProperty.linkId, username, value, timeStamp)
  }

  def insert(propertyName: String, linkProperty: LinkProperties, username: Option[String]) = {
    val dao = getDao(propertyName)
    val value = dao.getValue(linkProperty)
    dao.insertValues(linkProperty, username, value)
  }


  def update(propertyName: String, linkId: Long, username: Option[String], value: Int, existingValue: Int) = {
    val dao = getDao(propertyName)

    if(existingValue != value)
      dao.updateValues(linkId, username, value)
  }

  def update(propertyName: String, linkProperty: LinkProperties, vvhRoadLink: VVHRoadlink, username: Option[String], existingValue: Int, mmlId: Option[Long]) = {
    val dao = getDao(propertyName)
    val value = dao.getValue(linkProperty)

    if(existingValue != value)
      dao.updateValues(linkProperty, vvhRoadLink, username, value, mmlId)
  }

  def update(propertyName: String, linkProperty: LinkProperties, username: Option[String], existingValue: Int) = {
    val dao = getDao(propertyName)
    val value = dao.getValue(linkProperty)

    if(existingValue != value)
      dao.updateValues(linkProperty, username, value)

  }

  def delete(propertyName: String, linkId: Long) = {
    val dao = getDao(propertyName)
    dao.deleteValues(linkId)
  }


  case object FunctionalClassDao extends RoadLinkDAO {

    def table: String = FunctionalClass
    def column: String = FunctionalClass

    def getValue(linkProperty: LinkProperties): Int = {
      linkProperty.functionalClass
    }

    def getVVHValue(vvhRoadLink: VVHRoadlink): Option[Int] = {
       None
    }
  }

  case object TrafficDirectionDao extends RoadLinkDAO {

    def table: String = TrafficDirection
    def column: String = TrafficDirection

    def getValue(linkProperty: LinkProperties): Int ={
      linkProperty.trafficDirection.value
    }

    def getVVHValue(vvhRoadLink: VVHRoadlink): Option[Int] = {
      Some(vvhRoadLink.trafficDirection.value)
    }

    override def insertValues(linkProperty: LinkProperties, username: Option[String], value: Int): Unit = {
      sqlu"""insert into #$table (id, link_id, #$column, modified_by, link_type)
                   select primary_key_seq.nextval, ${linkProperty.linkId}, ${value}, $username, ${linkProperty.linkType.value}
                   from dual
                   where not exists (select * from #$table where link_id = ${linkProperty.linkId})""".execute
    }

    override def updateValues(linkProperty: LinkProperties, username: Option[String], value: Int): Unit = {
      sqlu"""update #$table
               set #$column = $value,
                   modified_date = SYSDATE,
                   modified_by = $username,
                   link_type = ${linkProperty.linkType.value}
               where link_id = ${linkProperty.linkId}""".execute

    }
  }

  case object LinkTypeDao extends RoadLinkDAO {

    def table: String = LinkType
    def column: String = LinkType

    def getValue(linkProperty: LinkProperties): Int ={
      linkProperty.linkType.value
    }

    def getVVHValue(vvhRoadLink: VVHRoadlink): Option[Int] = {
      None
    }

    def getAllLinkType(linkIds: Seq[Long]) = {
      val linkTypeInfo = MassQuery.withIds(linkIds.toSet) { idTableName =>
        sql"""
        select lt.link_id, lt.link_type
           from link_type lt
           join  #$idTableName i on i.id = lt.link_id
         """.as[(Long, Int)].list
      }
      linkTypeInfo.map {
        case (linkId, linkType) =>
          (linkId, asset.LinkType.apply(linkType))
      }
    }

  }

  case object AdministrativeClassDao extends RoadLinkDAO {

    def table: String = AdministrativeClass
    def column: String = AdministrativeClass

    override def getExistingValue(linkId: Long): Option[Int]= {
      sql"""select #$column from #$table where link_id = $linkId and (valid_to IS NULL OR valid_to > sysdate) """.as[Int].firstOption
    }

    def getValue(linkProperty: LinkProperties): Int ={
      linkProperty.administrativeClass.value
    }

    def getVVHValue(vvhRoadLink: VVHRoadlink): Option[Int] = {
      Some(vvhRoadLink.administrativeClass.value)
    }

    override def insertValues(linkProperty: LinkProperties, vvhRoadLink: VVHRoadlink, username: Option[String], value: Int, mmlId: Option[Long]): Unit = {
      val vvhValue = getVVHValue(vvhRoadLink)
      sqlu"""insert into #$table (id, link_id, #$column, created_by, mml_id, #$VVHAdministrativeClass )
                   select primary_key_seq.nextval, ${linkProperty.linkId}, $value, $username, $mmlId, ${vvhValue}
                   from dual
                   where not exists (select * from #$table where link_id = ${linkProperty.linkId})""".execute
    }

    override def updateValues(linkProperty: LinkProperties, vvhRoadLink: VVHRoadlink, username: Option[String], value: Int, mml_id: Option[Long] = None): Unit = {
      expireValues(linkProperty.linkId, username)
      val vvhValue = getVVHValue(vvhRoadLink)

      sqlu"""insert into #$table (id, link_id, #$column, created_by, mml_id, #$VVHAdministrativeClass )
                   select primary_key_seq.nextval, ${linkProperty.linkId}, $value, $username, $mml_id, $vvhValue
                   from dual
                   where exists (select * from #$table where link_id = ${linkProperty.linkId})""".execute

    }

    override def updateValues(linkId: Long, username: Option[String], value: Int) = {
      throw new UnsupportedOperationException("Administrative Class keeps history, should be used the update values implemented")
    }

    override def deleteValues(linkId: Long) = {
      throw new UnsupportedOperationException("Administrative Class keeps history, ins't suppost to be deleted any row from db")
    }
  }

//  case object TempRoadAddressesInfo {
//
//    def getByLinkId(linkIds: Set[Long]): Seq[RoadAddressTEMP] = {
//      val resultx = sql"""select link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m, side_code  from temp_road_address_info where link_id in (#${linkIds.mkString(",")})"""
//        .as[(Long, Int, Long, Long, Int, Long, Long, Option[Int])].list
//
//        resultx.map { case (linkId, municipalityCode, roadNumber, roadPart, trackCode, startAddressM, endAddressM, sideCode) =>
//          RoadAddressTEMP(linkId, municipalityCode, roadNumber, roadPart, Track.apply(trackCode), startAddressM, endAddressM, sideCode)
//        }
//    }
//
//    def getByRoadNumber(roadNumber: Int): Seq[RoadAddressTEMP] = {
//      val resultx = sql"""select link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m  from temp_road_address_info where road_number = $roadNumber"""
//        .as[(Long, Int, Long, Long, Int, Long, Long)].list
//
//      resultx.map { case (linkId, municipalityCode, roadNumber, roadPart, trackCode, startAddressM, endAddressM) =>
//        RoadAddressTEMP(linkId, municipalityCode, roadNumber, roadPart, Track.apply(trackCode), startAddressM, endAddressM)
//      }
//    }
//
//    def insertInfo(roadAddressTemp: RoadAddressTEMP, username: String): Unit = {
//      sqlu"""insert into temp_road_address_info (id, link_Id, municipality_code, road_number, road_part, track_code, start_address_m, end_address_m, side_code  ,created_by)
//             select primary_key_seq.nextval, ${roadAddressTemp.linkId}, ${roadAddressTemp.municipalityCode}, ${roadAddressTemp.road}, ${roadAddressTemp.roadPart}, ${roadAddressTemp.track.value}, ${roadAddressTemp.startAddressM}, ${roadAddressTemp.endAddressM},  ${roadAddressTemp.sideCode}, $username
//              from dual""".execute
//    }
//
//    def deleteInfoByMunicipality(municipalityCode: Int): Unit = {
//      sqlu"""delete from temp_road_address_info
//                 where municipality_code = $municipalityCode""".execute
//    }
//  }

  case object LinkAttributesDao extends RoadLinkDAO {

    def table: String = LinkAttributes
    def column: String = LinkAttributes

    def getExistingValues(linkId: Long) = {
      sql"""select name, value from #$table where link_id = $linkId and (valid_to IS NULL OR valid_to > sysdate) """.as[(String, String)].list.toMap
    }

    def getAllExistingDistinctValues(attributeName: String) : List[String] = {
      sql"""select distinct value from #$table where name = $attributeName and (valid_to is null or valid_to > sysdate)""".as[String].list
    }

    def getValuesByRoadAssociationName(roadAssociationName: String, attributeName: String): List[(String, Long)] = {
      sql"""select value, link_id from #$table where name = $attributeName
           and (valid_to is null or valid_to > sysdate) and value = $roadAssociationName""".as[(String, Long)].list
    }

    def insertAttributeValue(linkProperty: LinkProperties, username: String, attributeName: String, value: String): Unit = {
      sqlu"""insert into road_link_attributes (id, link_id, name, value, created_by )
             select primary_key_seq.nextval, ${linkProperty.linkId}, $attributeName, $value, $username
              from dual""".execute
    }

    def updateAttributeValue(linkProperty: LinkProperties, username: String, attributeName: String, value: String): Unit = {
      sqlu"""
            update road_link_attributes set
              value = $value,
              modified_date = sysdate,
              modified_by = $username
            where link_id = ${linkProperty.linkId}
            	and name = $attributeName
            	and (valid_to is null or valid_to > sysdate)
          """.execute
    }

    def expireAttributeValue(linkProperty: LinkProperties, username: String, attributeName: String): Unit = {
      sqlu"""
            update road_link_attributes
            set valid_to = sysdate - 1,
                modified_by = $username
            where link_id = ${linkProperty.linkId}
            	and name = $attributeName
              and (valid_to is null or valid_to > sysdate)
          """.execute
    }


    def getValue(linkProperty: LinkProperties): Int = {
      throw new UnsupportedOperationException("Method getValue is not supported for Link Attributes class")
    }

    def getVVHValue(vvhRoadLink: VVHRoadlink) = {
      throw new UnsupportedOperationException("Method getVVHValue is not supported for Link Attributes class")
    }

    override def expireValues(linkId: Long, username: Option[String]) = {
      sqlu"""update #$table
                 set valid_to = SYSDATE - 1,
                     modified_by = $username
                 where link_id = $linkId
                    and (valid_to is null or valid_to > sysdate)
        """.execute
    }
  }
}

