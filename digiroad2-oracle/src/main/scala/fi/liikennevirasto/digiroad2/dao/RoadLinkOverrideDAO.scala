package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass => AdminClassType}
import fi.liikennevirasto.digiroad2.client.RoadLinkFetched
import fi.liikennevirasto.digiroad2.postgis.{MassQuery, PostGISDatabase}
import fi.liikennevirasto.digiroad2.service.{IncompleteLink, LinkProperties, LinkPropertiesEntries}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}
import slick.jdbc.StaticQuery.interpolation

case class RoadLinkValue(linkId: String, value: Option[Int])
case class RoadLinkAttribute(linkId: String, attributes: Map[String, String])
case class MassOperationEntry(linkProperty: LinkProperties, username: Option[String], value: Int, timeStamp:Option[String], mmlId: Option[Long])

sealed trait RoadLinkOverrideDAO{

  def table: String
  def column : String

  def getValue(linkProperty: LinkProperties) : Int

  def getMasterDataValue(roadLinkFetched: RoadLinkFetched): Option[Int]

  def getExistingValue(linkId: String): Option[Int]= {
    sql"""select #$column from #$table where link_id = $linkId""".as[Int].firstOption
  }

  implicit val getRoadLinkRow: GetResult[RoadLinkValue] = new GetResult[RoadLinkValue] {
    def apply(r: PositionedResult): RoadLinkValue = {
      val id = r.nextString()
      val value = r.nextIntOption()
      RoadLinkValue(id, value)
    }
  }

  def getExistingValues(linkIds: Seq[String]): Seq[RoadLinkValue] = {
    if (linkIds.nonEmpty) {
      Q.queryNA[RoadLinkValue](s"""select link_id,$column from $table where link_id IN (${linkIds.map(id => s"'$id'").mkString(",")})""")(getRoadLinkRow).iterator.toSeq
    } else Seq()
  }

  def getLinkIdByValue(value: Int, since: Option[String]): Seq[String] = {
    val sinceDateQuery = since match {
      case Some(date) => " AND modified_date >= to_date('" + date + "', 'YYYYMMDD')"
      case _ =>""
    }

    sql"""select link_id from #$table where #$column = $value #$sinceDateQuery""".as[String].list
  }


  def insertValues(linkProperty: LinkProperties, roadLinkFetched: RoadLinkFetched, username: Option[String], value: Int, mmlId: Option[Long]): Unit = {
    insertValues(linkProperty, username, value)
  }

  def insertValuesMass(entries: Seq[MassOperationEntry]): Unit = {
    val insertLinkPropertyPS = dynamicSession.prepareStatement(
      s"""insert into $table (id, link_id, $column,modified_date, modified_by )
         select nextval('primary_key_seq'),(?),(?),to_timestamp((?), 'YYYY-MM-DD"T"HH24:MI:SS.ff3"+"TZH:TZM'),(?)
         where not exists (select * from $table where link_id =(?))""".stripMargin)
    try {
      entries.foreach { property =>
        insertLinkPropertyPS.setString(1, property.linkProperty.linkId)
        insertLinkPropertyPS.setInt(2, property.value)
        insertLinkPropertyPS.setString(3, property.timeStamp.get)
        insertLinkPropertyPS.setString(4, property.username.getOrElse(""))
        insertLinkPropertyPS.setString(5, property.linkProperty.linkId)
        insertLinkPropertyPS.addBatch()
      }
      insertLinkPropertyPS.executeBatch()
    } finally {
      insertLinkPropertyPS.close()
    }
  }

  def insertValuesMass(linkValues: Seq[RoadLinkValue], username: Option[String], timeStamp: String) = {
    if (linkValues.nonEmpty) {
      val insertSQL =
        s"""
      insert into $table (id, link_id, $column, modified_date, modified_by)
      select nextval('primary_key_seq'), ?, ?, to_timestamp(?, 'YYYY-MM-DD"T"HH24:MI:SS.ff3"+"TZH:TZM'), ?
      where not exists (select * from $table where link_id = ?)
      """.stripMargin
      MassQuery.executeBatch(insertSQL) { preparedStatement =>
        linkValues.foreach { case RoadLinkValue(linkId, Some(value)) =>
          preparedStatement.setString(1, linkId)
          preparedStatement.setInt(2, value)
          preparedStatement.setString(3, timeStamp)
          preparedStatement.setString(4, username.getOrElse(""))
          preparedStatement.setString(5, linkId)
          preparedStatement.addBatch()
        }
      }
    }
  }

  def updateValuesMass(entries: Seq[MassOperationEntry]): Unit = {
    val updateLinkPropertyPS = dynamicSession.prepareStatement(
      s"""update $table
          set $column = (?),
          modified_date = current_timestamp,
          modified_by = (?)
          where link_id = (?)""".stripMargin)
    try {
      entries.foreach { property =>
        updateLinkPropertyPS.setLong(1, property.value)
        updateLinkPropertyPS.setString(2, property.username.getOrElse(""))
        updateLinkPropertyPS.setString(3, property.linkProperty.linkId)
        updateLinkPropertyPS.addBatch()
      }
      updateLinkPropertyPS.executeBatch()
    } finally {
      updateLinkPropertyPS.close()
    }
  }

  def insertValues(linkProperty: LinkProperties, username: Option[String], value: Int): Unit = {
    sqlu""" insert into #$table (id, link_id, #$column, modified_by )
            select nextval('primary_key_seq'), ${linkProperty.linkId}, $value, $username
            where not exists (select * from #$table where link_id =${linkProperty.linkId})""".execute
  }

  def insertValues(linkId: String, username: Option[String], value: Int) = {
    sqlu""" insert into #$table (id, link_id, #$column, modified_by )
            select nextval('primary_key_seq'), $linkId, $value, $username
            where not exists (select * from #$table where link_id = $linkId)""".execute
  }

  def insertValues(linkId: String, username: Option[String], value: Int, timeStamp: String) = {
    sqlu""" insert into #$table (id, link_id, #$column, modified_date, modified_by)
            select nextval('primary_key_seq'), $linkId, $value,to_timestamp($timeStamp, 'YYYY-MM-DD"T"HH24:MI:SS.ff3"+"TZH:TZM'), $username
           where not exists (select * from #$table where link_id = $linkId)""".execute
  }


  def updateValues(linkProperty: LinkProperties, roadLinkFetched: RoadLinkFetched, username: Option[String], value: Int, mml_id: Option[Long]): Unit = {
    sqlu"""update #$table
               set #$column = $value,
                   modified_date = current_timestamp,
                   modified_by = $username
               where link_id = ${linkProperty.linkId}""".execute
  }

  def updateValues(linkProperty: LinkProperties, username: Option[String], value: Int): Unit = {
    sqlu"""update #$table
               set #$column = $value,
                   modified_date = current_timestamp,
                   modified_by = $username
               where link_id = ${linkProperty.linkId}""".execute
  }

  def updateValues(linkId: String, username: Option[String], value: Int): Unit = {
    sqlu"""update #$table
               set #$column = $value,
                   modified_date = current_timestamp,
                   modified_by = $username
               where link_id = $linkId""".execute
  }


  def expireValues(linkId: String, username: Option[String], changeTimeStamp: Option[Long] = None) = {
    val withTimeStamp = changeTimeStamp match {
      case Some(ts) => ", adjusted_timestamp = " + ts + ""
      case _ => ""
    }

    sqlu"""update #$table
                 set valid_to = current_timestamp - INTERVAL'1 DAYS',
                     modified_by = $username
                     #$withTimeStamp
                 where link_id = $linkId
                    and (valid_to is null or valid_to > current_timestamp)
        """.execute
  }

  def deleteValues(linkId: String) = {
    sqlu"""delete from #$table
                 where link_id = $linkId""".execute
  }
}

object RoadLinkOverrideDAO{

  val FunctionalClass = "functional_class"
  val TrafficDirection = "traffic_direction"
  val LinkType = "link_type"
  val AdministrativeClass = "administrative_class"
  val MasterDataAdministrativeClass = "master_data_administrative_class"
  val LinkAttributes = "road_link_attributes"
  val IncompleteLinks = "incomplete_link"

  private def getDao(propertyName: String): RoadLinkOverrideDAO = {
    propertyName.toLowerCase match {
      case FunctionalClass => FunctionalClassDao
      case TrafficDirection => TrafficDirectionDao
      case LinkType => LinkTypeDao
      case AdministrativeClass => AdministrativeClassDao
      case LinkAttributes => LinkAttributesDao
      case IncompleteLinks => IncompleteLinkDao
    }
  }

  def get(propertyName: String, linkId: String): Option[Int]= {
    val dao = getDao(propertyName)
    dao.getExistingValue(linkId)
  }

  def getValues(propertyName: String, linkIds: Seq[String]): Seq[RoadLinkValue]= {
    val dao = getDao(propertyName)
    dao.getExistingValues(linkIds)
  }

  def getValue(propertyName: String, linkProperty: LinkProperties): Int  = {
    val dao = getDao(propertyName)
    dao.getValue(linkProperty)
  }

  def getMasterDataValue(propertyName: String, roadLinkFetched: RoadLinkFetched): Option[Int] = {
    val dao = getDao(propertyName)
    dao.getMasterDataValue(roadLinkFetched)
  }

  def getLinkIdByValue(propertyName: String, value: Int, since: Option[String]): Seq[String] = {
    val dao = getDao(propertyName)
    dao.getLinkIdByValue(value, since)
  }


  def insert(propertyName: String, linkProperty: LinkProperties, roadLinkFetched: RoadLinkFetched, username: Option[String], mmlId: Option[Long]) = {
    val dao = getDao(propertyName)
    val value = dao.getValue(linkProperty)
    dao.insertValues(linkProperty, roadLinkFetched, username, value, mmlId)
  }

  def insertMass(propertyName: String, entries: Seq[LinkPropertiesEntries]):Unit = {
    val dao = getDao(propertyName)
    dao.insertValuesMass(entries.map(e => {
      val value = dao.getValue(e.linkProperty)
      MassOperationEntry(e.linkProperty, e.username, value,e.latestModifiedAt, e.mmlId)
    }))
  }
  def updateMass(propertyName: String, entries: Seq[LinkPropertiesEntries]):Unit = {
    val dao = getDao(propertyName)
    dao.updateValuesMass(entries.map(e => {
      val value = dao.getValue(e.linkProperty)
      MassOperationEntry(e.linkProperty, e.username, value,e.latestModifiedAt, e.mmlId)
    }))
  }

  def insert(propertyName: String, linkId: String, username: Option[String], value: Int) = {
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


  def update(propertyName: String, linkId: String, username: Option[String], value: Int, existingValue: Int) = {
    val dao = getDao(propertyName)

    if(existingValue != value)
      dao.updateValues(linkId, username, value)
  }

  def update(propertyName: String, linkProperty: LinkProperties, roadLinkFetched: RoadLinkFetched, username: Option[String], existingValue: Int, mmlId: Option[Long]) = {
    val dao = getDao(propertyName)
    val value = dao.getValue(linkProperty)

    if(existingValue != value)
      dao.updateValues(linkProperty, roadLinkFetched, username, value, mmlId)
  }

  def update(propertyName: String, linkProperty: LinkProperties, username: Option[String], existingValue: Int, mmlId: Option[Long] = None) = {
    val dao = getDao(propertyName)
    val value = dao.getValue(linkProperty)

    if(existingValue != value)
      dao.updateValues(linkProperty, username, value)

  }

  def delete(propertyName: String, linkId: String) = {
    val dao = getDao(propertyName)
    dao.deleteValues(linkId)
  }


  case object FunctionalClassDao extends RoadLinkOverrideDAO {

    def table: String = FunctionalClass
    def column: String = FunctionalClass

    def getValue(linkProperty: LinkProperties): Int = {
      linkProperty.functionalClass
    }

    def getMasterDataValue(roadLinkFetched: RoadLinkFetched): Option[Int] = {
      None
    }
  }

  case object TrafficDirectionDao extends RoadLinkOverrideDAO {

    def table: String = TrafficDirection
    def column: String = TrafficDirection

    def getValue(linkProperty: LinkProperties): Int ={
      linkProperty.trafficDirection.value
    }

    def getMasterDataValue(roadLinkFetched: RoadLinkFetched): Option[Int] = {
      Some(roadLinkFetched.trafficDirection.value)
    }

    override def insertValues(linkProperty: LinkProperties, username: Option[String], value: Int): Unit = {
      sqlu"""insert into #$table (id, link_id, #$column, modified_by, link_type)
             select nextval('primary_key_seq'), ${linkProperty.linkId}, ${value}, $username, ${linkProperty.linkType.value}
                   where not exists (select * from #$table where link_id = ${linkProperty.linkId})""".execute
    }

    override def insertValuesMass(entries: Seq[MassOperationEntry]): Unit = {

      val insertLinkPropertyPS = dynamicSession.prepareStatement(
        s"""insert into $table (id, link_id, $column, modified_by,link_type )
           select nextval('primary_key_seq'),(?),(?),(?),(?)
           where not exists (select * from $table where link_id =(?))""".stripMargin)
      try {
        entries.foreach { property =>
          insertLinkPropertyPS.setString(1, property.linkProperty.linkId)
          insertLinkPropertyPS.setInt(2, property.value)
          insertLinkPropertyPS.setString(3, property.username.getOrElse(""))
          insertLinkPropertyPS.setInt(4, property.linkProperty.linkType.value)
          insertLinkPropertyPS.setString(5, property.linkProperty.linkId)
          insertLinkPropertyPS.addBatch()
        }
        insertLinkPropertyPS.executeBatch()
      } finally {
        insertLinkPropertyPS.close()
      }
    }

    override def updateValues(linkProperty: LinkProperties, username: Option[String], value: Int): Unit = {
      sqlu"""update #$table
               set #$column = $value,
                   modified_date = current_timestamp,
                   modified_by = $username,
                   link_type = ${linkProperty.linkType.value}
               where link_id = ${linkProperty.linkId}""".execute

    }

    def getLinkIds(): Seq[String] = {
      sql"""select link_id from traffic_direction""".as[String].list
    }
    override def updateValuesMass(entries:Seq[MassOperationEntry]): Unit ={
      val updateLinkPropertyPS = dynamicSession.prepareStatement(
        s"""update $table
            set $column = (?),
            modified_date = current_timestamp,
            modified_by = (?),
            link_type = (?) where link_id = (?)"""
      )
      try {
        entries.foreach { property =>
          updateLinkPropertyPS.setInt(1, property.value)
          updateLinkPropertyPS.setString(2, property.username.getOrElse(""))
          updateLinkPropertyPS.setInt(3, property.linkProperty.linkType.value)
          updateLinkPropertyPS.setString(4, property.linkProperty.linkId)
          updateLinkPropertyPS.addBatch()
        }
        updateLinkPropertyPS.executeBatch()
      } finally {
        updateLinkPropertyPS.close()
      }
    }

  }

  case object LinkTypeDao extends RoadLinkOverrideDAO {

    def table: String = LinkType
    def column: String = LinkType

    def getValue(linkProperty: LinkProperties): Int ={
      linkProperty.linkType.value
    }

    def getMasterDataValue(roadLinkFetched: RoadLinkFetched): Option[Int] = {
      None
    }

    def getAllLinkType(linkIds: Seq[String]) = {
      val linkTypeInfo = MassQuery.withStringIds(linkIds.toSet) { idTableName =>
        sql"""
        select lt.link_id, lt.link_type
           from link_type lt
           join  #$idTableName i on i.id = lt.link_id
         """.as[(String, Int)].list
      }
      linkTypeInfo.map {
        case (linkId, linkType) =>
          (linkId, asset.LinkType.apply(linkType))
      }
    }
 }

  case object AdministrativeClassDao extends RoadLinkOverrideDAO {

    def table: String = AdministrativeClass
    def column: String = AdministrativeClass

    override def getExistingValue(linkId: String): Option[Int]= {
      sql"""select #$column from #$table where link_id = $linkId and (valid_to IS NULL OR valid_to > current_timestamp) """.as[Int].firstOption
    }

    def getValue(linkProperty: LinkProperties): Int ={
      linkProperty.administrativeClass.value
    }

    def getMasterDataValue(roadLinkFetched: RoadLinkFetched): Option[Int] = {
      Some(roadLinkFetched.administrativeClass.value)
    }

    override def insertValues(linkProperty: LinkProperties, roadLinkFetched: RoadLinkFetched, username: Option[String], value: Int, mmlId: Option[Long]): Unit = {
      val masterDataValue = getMasterDataValue(roadLinkFetched)
      sqlu"""insert into #$table (id, link_id, #$column, created_by, mml_id, #$MasterDataAdministrativeClass )
             select nextval('primary_key_seq'), ${linkProperty.linkId}, $value, $username, $mmlId, ${masterDataValue}
              where not exists (select * from #$table where link_id = ${linkProperty.linkId})""".execute
    }

    def insertValuesMass(linkValues: Seq[RoadLinkValue], username: Option[String]) = {
      if (linkValues.nonEmpty) {
        val insertSQL =
          s"""
        insert into $table (id, link_id, $column, modified_by)
        select nextval('primary_key_seq'), ?, ?, ?
        where not exists (select * from $table where link_id = ?)
        """.stripMargin
        MassQuery.executeBatch(insertSQL) { preparedStatement =>
          linkValues.foreach { case RoadLinkValue(linkId, Some(value)) =>
            preparedStatement.setString(1, linkId)
            preparedStatement.setInt(2, value)
            preparedStatement.setString(3, username.getOrElse(""))
            preparedStatement.setString(4, linkId)
            preparedStatement.addBatch()
          }
        }
      }
    }

    override def expireValues(linkId: String, username: Option[String], changeTimeStamp: Option[Long] = None) = {
      sqlu"""update #$table
                 set valid_to = current_timestamp - INTERVAL'1 DAYS',
                     modified_by = $username
                 where link_id = $linkId""".execute
    }

    override def updateValues(linkProperty: LinkProperties, roadLinkFetched: RoadLinkFetched, username: Option[String], value: Int, mml_id: Option[Long] = None): Unit = {
      expireValues(linkProperty.linkId, username)
      val masterDataValue = getMasterDataValue(roadLinkFetched)

      sqlu"""insert into #$table (id, link_id, #$column, created_by, mml_id, #$MasterDataAdministrativeClass )
             select nextval('primary_key_seq'), ${linkProperty.linkId}, $value, $username, $mml_id, $masterDataValue
                   where exists (select * from #$table where link_id = ${linkProperty.linkId})""".execute

    }

    override def updateValues(linkId: String, username: Option[String], value: Int) = {
      throw new UnsupportedOperationException("Administrative Class keeps history, should be used the update values implemented")
    }

    override def insertValuesMass(entries: Seq[MassOperationEntry]): Unit = {
      throw new UnsupportedOperationException("Not Implemented")
    }

    override def updateValuesMass(entries: Seq[MassOperationEntry]): Unit = {
      throw new UnsupportedOperationException("Administrative Class keeps history, should be used the update values implemented")
    }

    override def deleteValues(linkId: String) = {
      throw new UnsupportedOperationException("Administrative Class keeps history, ins't suppost to be deleted any row from db")
    }

    def getExistingValueByLinkIds(linkIds: Seq[Long]) = {
      val linkTypeInfo = MassQuery.withIds(linkIds.toSet) { idTableName =>
        sql"""
        select t.link_id, #$column
           from #$table t
           join  #$idTableName i on i.id = t.link_id
         """.as[(String, Int)].list
      }
      linkTypeInfo.map {
        case (linkId, administrativeClassValue) =>
          (linkId, asset.AdministrativeClass(administrativeClassValue))
      }
    }

  }

  case object LinkAttributesDao extends RoadLinkOverrideDAO {

    def table: String = LinkAttributes
    def column: String = LinkAttributes

    def getExistingValues(linkId: String, changeTimeStamp: Option[Long] = None ) = {
      val withTimeStamp = changeTimeStamp match {
        case Some(ts) => "and adjusted_timestamp < " + ts + ""
        case _ => ""
      }

      sql"""select name, value from #$table where link_id = $linkId and (valid_to IS NULL OR valid_to > current_timestamp #$withTimeStamp) """.as[(String, String)].list.toMap
    }

    def insertAttributeValueByChanges(linkId: String, username: String, attributeName: String, value: String, changeTimeStamp: Long): Unit = {
      sqlu"""insert into road_link_attributes (id, link_id, name, value, created_by, adjusted_timestamp)values(
             nextval('primary_key_seq'), $linkId, $attributeName, $value, $username, $changeTimeStamp)""".execute
    }

    def getAllExistingDistinctValues(attributeName: String) : List[String] = {
      sql"""select distinct value from #$table where name = $attributeName and (valid_to is null or valid_to > current_timestamp)""".as[String].list
    }

    def getValuesByRoadAssociationName(roadAssociationName: String, attributeName: String): List[(String, String)] = {
      sql"""select value, link_id from #$table where name = $attributeName
           and (valid_to is null or valid_to > current_timestamp) and trim(replace(upper(value), '\s{2,}', ' ')) = $roadAssociationName""".as[(String, String)].list
    }

    def insertAttributeValue(linkProperty: LinkProperties, username: String, attributeName: String, value: String, mmlId: Option[Long]): Unit = {
      sqlu"""insert into road_link_attributes (id, link_id, name, value, created_by, mml_id )values (
             nextval('primary_key_seq'), ${linkProperty.linkId}, $attributeName, $value, $username, $mmlId)""".execute
    }

    def updateAttributeValue(linkProperty: LinkProperties, username: String, attributeName: String, value: String): Unit = {
      sqlu"""
            update road_link_attributes set
              value = $value,
              modified_date = current_timestamp,
              modified_by = $username
            where link_id = ${linkProperty.linkId}
            	and name = $attributeName
            	and (valid_to is null or valid_to > current_timestamp)
          """.execute
    }

    def expireAttributeValue(linkProperty: LinkProperties, username: String, attributeName: String): Unit = {
      sqlu"""
            update road_link_attributes
            set valid_to = current_timestamp - INTERVAL'1 DAYS',
                modified_by = $username
            where link_id = ${linkProperty.linkId}
            	and name = $attributeName
              and (valid_to is null or valid_to > current_timestamp)
          """.execute
    }

    override def insertValuesMass(entries: Seq[MassOperationEntry]): Unit = {
      throw new UnsupportedOperationException("Not Implemented")
    }

    def insertValuesMass(linkAttributes: Seq[RoadLinkAttribute], username: Option[String], timeStamp: String) = {
      if (linkAttributes.nonEmpty) {
        val insertSQL =
          s"""
          insert into $table (id, link_id, name, value, modified_date, modified_by)
          select nextval('primary_key_seq'), ?, ?, ?, to_timestamp(?, 'YYYY-MM-DD"T"HH24:MI:SS.ff3"+"TZH:TZM'), ?
          where not exists (select * from $table where link_id = ? and name = ?)
          """.stripMargin
        MassQuery.executeBatch(insertSQL) { preparedStatement =>
          linkAttributes.foreach { case RoadLinkAttribute(linkId, attributes) =>
            attributes.foreach { case (attributeName, attributeValue) =>
              preparedStatement.setString(1, linkId)
              preparedStatement.setString(2, attributeName)
              preparedStatement.setString(3, attributeValue)
              preparedStatement.setString(4, timeStamp)
              preparedStatement.setString(5, username.getOrElse(""))
              preparedStatement.setString(6, linkId)
              preparedStatement.setString(7, attributeName)
              preparedStatement.addBatch()
            }
          }
        }
      }
    }

    override def updateValuesMass(entries: Seq[MassOperationEntry]): Unit = {
      throw new UnsupportedOperationException("Not Implemented")
    }

    def getValue(linkProperty: LinkProperties): Int = {
      throw new UnsupportedOperationException("Method getValue is not supported for Link Attributes class")
    }

    def getMasterDataValue(roadLinkFetched: RoadLinkFetched) = {
      throw new UnsupportedOperationException("Method getMasterDataValue is not supported for Link Attributes class")
    }
  }

  case object IncompleteLinkDao extends RoadLinkOverrideDAO {

    def table: String = IncompleteLinks
    def column: String = IncompleteLinks

    def getValue(linkProperty: LinkProperties): Int = {
      throw new UnsupportedOperationException("Method getValue is not supported for incomplete links")
    }

    def getMasterDataValue(roadLinkFetched: RoadLinkFetched) = {
      throw new UnsupportedOperationException("Method getMasterDataValue is not supported for imcomplete links")
    }

    def getIncompleteLinks(includedMunicipalities: Option[Set[Int]]): Map[String, Map[String, Seq[String]]] = {
      case class IncompleteLink(linkId: String, municipality: String, administrativeClass: String)
      def toIncompleteLink(x: (String, String, Int)) = IncompleteLink(x._1, x._2, AdminClassType(x._3).toString)

      val optionalMunicipalities = includedMunicipalities.map(_.mkString(","))
      val incompleteLinksQuery =
        """
        select l.link_id, m.name_fi, l.administrative_class
        from incomplete_link l
        join municipality m on l.municipality_code = m.id
                                 """

      val sql = optionalMunicipalities match {
        case Some(municipalities) => incompleteLinksQuery + s" where l.municipality_code in ($municipalities)"
        case _ => incompleteLinksQuery
      }

        Q.queryNA[(String, String, Int)](sql).list
          .map(toIncompleteLink)
          .groupBy(_.municipality)
          .mapValues {
            _.groupBy(_.administrativeClass)
              .mapValues(_.map(_.linkId))
          }
    }

    def insertIncompleteLinks(incompleteLinks: Seq[IncompleteLink]): Unit = {

      val insertLinkPropertyPS = dynamicSession.prepareStatement(
        s"""insert into incomplete_link(id, link_id, municipality_code, administrative_class)
           |select nextval('primary_key_seq'), (?), (?), (?)
           |where not exists (select * from incomplete_link where link_id = (?))""".stripMargin)
      try {
        incompleteLinks.foreach { incompleteLink =>
          insertLinkPropertyPS.setString(1, incompleteLink.linkId)
          insertLinkPropertyPS.setInt(2, incompleteLink.municipalityCode)
          insertLinkPropertyPS.setInt(3, incompleteLink.administrativeClass.value)
          insertLinkPropertyPS.setString(4, incompleteLink.linkId)
          insertLinkPropertyPS.addBatch()
        }
        insertLinkPropertyPS.executeBatch()
      } finally {
        insertLinkPropertyPS.close()
      }
    }
  }
}