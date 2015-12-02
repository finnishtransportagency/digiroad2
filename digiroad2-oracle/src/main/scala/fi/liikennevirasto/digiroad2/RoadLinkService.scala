package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.ConversionDatabase.GetPointSeq
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.VVHRoadLinkWithProperties
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}


case class IncompleteLink(mmlId: Long, municipalityCode: Int, administrativeClass: AdministrativeClass)
case class RoadLinkChangeSet(adjustedRoadLinks: Seq[VVHRoadLinkWithProperties], incompleteLinks: Seq[IncompleteLink])
case class LinkProperties(mmlId: Long, functionalClass: Int, linkType: LinkType, trafficDirection: TrafficDirection)

@deprecated("Use VVHRoadLinkWithProperties instead", "")
case class AdjustedRoadLink(id: Long, mmlId: Long, geometry: Seq[Point],
                            length: Double, administrativeClass: AdministrativeClass,
                            functionalClass: Int, trafficDirection: TrafficDirection,
                            modifiedAt: Option[String], modifiedBy: Option[String], linkType: Int)

trait RoadLinkService {
  case class BasicRoadLink(id: Long, mmlId: Long, geometry: Seq[Point], length: Double, administrativeClass: AdministrativeClass, trafficDirection: TrafficDirection, modifiedAt: Option[DateTime])
  val logger = LoggerFactory.getLogger(getClass)

  def eventbus: DigiroadEventBus

  def getByIdAndMeasure(id: Long, measure: Double): Option[(Long, Int, Option[Point], AdministrativeClass)] = {
    Database.forDataSource(ConversionDatabase.dataSource).withDynTransaction {
      val query = sql"""
         select dr1_id, kunta_nro, to_2d(sdo_lrs.dynamic_segment(shape, $measure, $measure)), omistaja
           from tielinkki_ctas
           where dr1_id = $id
        """
      query.as[(Long, Int, Seq[Point], AdministrativeClass)].firstOption.map {
        case (roadLinkId, municipalityNumber, geometry, roadLinkType) => (roadLinkId, municipalityNumber, geometry.headOption, roadLinkType)
      }
    }
  }

  def getByTestIdAndMeasure(testId: Long, measure: Double): Option[(Long, Int, Option[Point], AdministrativeClass)] = {
    Database.forDataSource(ConversionDatabase.dataSource).withDynTransaction {
       val query = sql"""
         select prod.dr1_id, prod.kunta_nro, to_2d(sdo_lrs.dynamic_segment(prod.shape, $measure, $measure)), prod.omistaja
           from tielinkki_ctas prod
           join tielinkki test
           on prod.mml_id = test.mml_id
           where test.objectid = $testId
        """
      query.as[(Long, Int, Seq[Point], AdministrativeClass)].list match {
        case List(productionLink) => Some((productionLink._1, productionLink._2, productionLink._3.headOption, productionLink._4))
        case _ => None
      }
    }
  }

  def getMunicipalityCode(roadLinkId: Long): Option[Int] = {
    Database.forDataSource(ConversionDatabase.dataSource).withDynTransaction {
       val query = sql"""
         select prod.kunta_nro
           from tielinkki_ctas prod
           where prod.dr1_id = $roadLinkId
        """
      query.as[Int].firstOption
    }
  }

  def getRoadLinkGeometry(id: Long): Option[Seq[Point]] = {
    fetchVVHRoadlink(id).map(_.geometry)
  }

  def getRoadLinkGeometryByTestId(testId: Long): Option[Seq[Point]] = {
    Database.forDataSource(ConversionDatabase.dataSource).withDynTransaction {
      val query = sql"""
        select to_2d(prod.shape)
           from tielinkki_ctas prod
           join tielinkki test
           on prod.mml_id = test.mml_id
           where test.objectid = $testId
        """
      query.as[Seq[Point]].firstOption
    }
  }

  def getTestId(id: Long): Option[Long] = {
    Database.forDataSource(ConversionDatabase.dataSource).withDynTransaction {
      val query = sql"""
        select test.objectid
           from tielinkki_ctas prod
           join tielinkki test
           on prod.mml_id = test.mml_id
           where prod.dr1_id = $id
        """
      query.as[Long].firstOption
    }
  }

  def getPointLRMeasure(roadLinkId: Long, point: Point): BigDecimal = {
    Database.forDataSource(ConversionDatabase.dataSource).withDynTransaction {
      val x = point.x
      val y = point.y
      val query =
        s"""
          SELECT
            SDO_LRS.GET_MEASURE(
              SDO_LRS.PROJECT_PT(
                tl.shape,
                MDSYS.SDO_GEOMETRY(2001,
                                   3067,
                                   NULL,
                                   MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                   MDSYS.SDO_ORDINATE_ARRAY($x, $y)
                                  )
                ))
            FROM tielinkki_ctas tl
            WHERE tl.dr1_id = $roadLinkId
        """
      Q.queryNA[BigDecimal](query).first
    }
  }

  implicit val getAdministrativeClass = new GetResult[AdministrativeClass] {
    def apply(r: PositionedResult) = {
      AdministrativeClass(r.nextInt())
    }
  }

  implicit val getTrafficDirection = new GetResult[TrafficDirection] {
    def apply(r: PositionedResult) = {
      TrafficDirection(r.nextIntOption())
    }
  }

  implicit val getBasicRoadLink = GetResult( r => BasicRoadLink(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, None) )

  def getRoadLinkMiddlePointByMMLId(mmlId: Long): Option[(Long, Point)]

  def getRoadLinkLength(id: Long): Option[Double] = {
    Database.forDataSource(ConversionDatabase.dataSource).withDynTransaction {
      val query = sql"""
        select sdo_lrs.geom_segment_length(shape) as length
          from tielinkki_ctas
          where dr1_id = $id
        """
      query.as[Double].firstOption
    }
  }

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  private def updateExistingLinkPropertyRow(table: String, column: String, mmlId: Long, username: String, existingValue: Int, value: Int) = {
    if (existingValue != value) {
      sqlu"""update #$table
               set #$column = $value,
                   modified_date = current_timestamp,
                   modified_by = $username
               where mml_id = $mmlId""".execute
    }
  }

  protected def setLinkProperty(table: String, column: String, value: Int, mmlId: Long, username: String, optionalVVHValue: Option[Int] = None) = {
    val optionalExistingValue: Option[Int] = sql"""select #$column from #$table where mml_id = $mmlId""".as[Int].firstOption
    (optionalExistingValue, optionalVVHValue) match {
      case (Some(existingValue), _) =>
        updateExistingLinkPropertyRow(table, column, mmlId, username, existingValue, value)
      case (None, None) =>
        sqlu"""insert into #$table (mml_id, #$column, modified_by)
                 select $mmlId, $value, $username
                 from dual
                 where not exists (select * from #$table where mml_id = $mmlId)""".execute
      case (None, Some(vvhValue)) =>
        if (vvhValue != value)
          sqlu"""insert into #$table (mml_id, #$column, modified_by)
                   select $mmlId, $value, $username
                   from dual
                   where not exists (select * from #$table where mml_id = $mmlId)""".execute
    }
  }

  def updateProperties(id: Long, functionalClass: Int, linkType: LinkType,
                       direction: TrafficDirection, username: String, municipalityValidation: Int => Unit): Option[VVHRoadLinkWithProperties]

  private def basicToAdjusted(basic: BasicRoadLink, modification: Option[(DateTime, String)], functionalClass: Int, linkType: Int, trafficDirection: TrafficDirection): AdjustedRoadLink = {
    val (modifiedAt, modifiedBy) = (modification.map(_._1), modification.map(_._2))
    AdjustedRoadLink(basic.id, basic.mmlId, basic.geometry, basic.length,
      basic.administrativeClass, functionalClass, trafficDirection, modifiedAt.map(DateTimePropertyFormat.print), modifiedBy, linkType)
  }

  implicit val getDateTime = new GetResult[DateTime] {
    def apply(r: PositionedResult) = {
      new DateTime(r.nextTimestamp())
    }
  }

  private def fetchTrafficDirections(idTableName: String): Seq[(Long, Int, DateTime, String)] = {
    sql"""select t.mml_id, t.traffic_direction, t.modified_date, t.modified_by
            from traffic_direction t
            join #$idTableName i on i.id = t.mml_id""".as[(Long, Int, DateTime, String)].list
  }

  private def fetchFunctionalClasses(idTableName: String): Seq[(Long, Int, DateTime, String)] = {
    sql"""select f.mml_id, f.functional_class, f.modified_date, f.modified_by
            from functional_class f
            join #$idTableName i on i.id = f.mml_id""".as[(Long, Int, DateTime, String)].list
  }

  private def fetchLinkTypes(idTableName: String): Seq[(Long, Int, DateTime, String)] = {
    sql"""select l.mml_id, l.link_type, l.modified_date, l.modified_by
            from link_type l
            join #$idTableName i on i.id = l.mml_id""".as[(Long, Int, DateTime, String)].list
  }

  private def adjustedRoadLinks(basicRoadLinks: Seq[BasicRoadLink]): Seq[AdjustedRoadLink] = {
    val (adjustedTrafficDirections, adjustedFunctionalClasses, adjustedLinkTypes) =
      MassQuery.withIds(basicRoadLinks.map(_.mmlId).toSet) { idTableName =>
        val trafficDirections: Map[Long, Seq[(Long, Int, DateTime, String)]] = fetchTrafficDirections(idTableName).groupBy(_._1)
        val functionalClasses: Map[Long, Seq[(Long, Int, DateTime, String)]] = fetchFunctionalClasses(idTableName).groupBy(_._1)
        val linkTypes: Map[Long, Seq[(Long, Int, DateTime, String)]] = fetchLinkTypes(idTableName).groupBy(_._1)
        (trafficDirections, functionalClasses, linkTypes)
      }

    basicRoadLinks.map { basicRoadLink =>
      val mmlId = basicRoadLink.mmlId
      val functionalClass = adjustedFunctionalClasses.get(mmlId).flatMap(_.headOption)
      val adjustedLinkType = adjustedLinkTypes.get(mmlId).flatMap(_.headOption)
      val trafficDirection = adjustedTrafficDirections.get(mmlId).flatMap(_.headOption)

      val functionalClassValue = functionalClass.map(_._2).getOrElse(FunctionalClass.Unknown)
      val adjustedLinkTypeValue = adjustedLinkType.map(_._2).getOrElse(UnknownLinkType.value)
      val trafficDirectionValue = trafficDirection.map(trafficDirection =>
        TrafficDirection(trafficDirection._2)
      ).getOrElse(basicRoadLink.trafficDirection)

      def latestModifications(a: Option[(DateTime, String)], b: Option[(DateTime, String)]) = {
        (a, b) match {
          case (Some((firstModifiedAt, firstModifiedBy)), Some((secondModifiedAt, secondModifiedBy))) =>
            if (firstModifiedAt.isAfter(secondModifiedAt))
              Some((firstModifiedAt, firstModifiedBy))
            else
              Some((secondModifiedAt, secondModifiedBy))
          case (Some((firstModifiedAt, firstModifiedBy)), None) => Some((firstModifiedAt, firstModifiedBy))
          case (None, Some((secondModifiedAt, secondModifiedBy))) => Some((secondModifiedAt, secondModifiedBy))
          case (None, None) => None
        }
      }
      val modifications = List(functionalClass, trafficDirection, adjustedLinkType).map {
        case Some((_, _, at, by)) => Some((at, by))
        case _ => None
      } :+ basicRoadLink.modifiedAt.map(at => (at, "vvh"))

      basicToAdjusted(basicRoadLink, modifications.reduce(latestModifications), functionalClassValue, adjustedLinkTypeValue, trafficDirectionValue)
    }
  }

  def getRoadLinkMmlId(id: Long): Long = {
    Database.forDataSource(ConversionDatabase.dataSource).withDynTransaction {
      sql"""select mml_id from tielinkki_ctas where dr1_id = $id""".as[Long].first
    }
  }

  def getRoadLinksFromVVH(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[VVHRoadLinkWithProperties] = {
    val vvhRoadLinks = fetchVVHRoadlinks(bounds, municipalities)
    withDynTransaction {
      enrichRoadLinksFromVVH(vvhRoadLinks)
    }
  }

  def getRoadLinksFromVVH(mmlIds: Set[Long]): Seq[VVHRoadLinkWithProperties] = {
    val vvhRoadLinks = fetchVVHRoadlinks(mmlIds)
    withDynTransaction {
      enrichRoadLinksFromVVH(vvhRoadLinks)
    }
  }

  def getRoadLinksFromVVH(municipality: Int): Seq[VVHRoadLinkWithProperties] = {
    val vvhRoadLinks = fetchVVHRoadlinks(municipality)
    withDynTransaction {
      enrichRoadLinksFromVVH(vvhRoadLinks)
    }
  }

  def getRoadLinkFromVVH(mmlId: Long): Option[VVHRoadLinkWithProperties] = {
    val vvhRoadLinks = fetchVVHRoadlinks(Set(mmlId))
    withDynTransaction {
      enrichRoadLinksFromVVH(vvhRoadLinks)
    }.headOption
  }

  protected def removeIncompleteness(mmlId: Long) = {
    sqlu"""delete from incomplete_link where mml_id = $mmlId""".execute
  }

  def updateRoadLinkChanges(roadLinkChangeSet: RoadLinkChangeSet): Unit = {
    updateAutoGeneratedProperties(roadLinkChangeSet.adjustedRoadLinks)
    updateIncompleteLinks(roadLinkChangeSet.incompleteLinks)
  }

  def updateAutoGeneratedProperties(adjustedRoadLinks: Seq[VVHRoadLinkWithProperties]) {
    def updateProperties(roadLink: VVHRoadLinkWithProperties) = {
      setLinkProperty("functional_class", "functional_class", roadLink.functionalClass, roadLink.mmlId, "automatic_generation")
      setLinkProperty("link_type", "link_type", roadLink.linkType.value, roadLink.mmlId, "automatic_generation")
    }
    withDynTransaction {
      adjustedRoadLinks.foreach(updateProperties)
      adjustedRoadLinks.foreach(link => removeIncompleteness(link.mmlId))
    }
  }

  protected def updateIncompleteLinks(incompleteLinks: Seq[IncompleteLink]) = {
    def setIncompleteness(incompleteLink: IncompleteLink) {
      withDynTransaction {
        sqlu"""insert into incomplete_link(mml_id, municipality_code, administrative_class)
                 select ${incompleteLink.mmlId}, ${incompleteLink.municipalityCode}, ${incompleteLink.administrativeClass.value} from dual
                 where not exists (select * from incomplete_link where mml_id = ${incompleteLink.mmlId})""".execute
      }
    }
    incompleteLinks.foreach(setIncompleteness)
  }

  protected def enrichRoadLinksFromVVH(vvhRoadLinks: Seq[VVHRoadlink]): Seq[VVHRoadLinkWithProperties] = {
    def autoGenerateProperties(roadLink: VVHRoadLinkWithProperties): VVHRoadLinkWithProperties = {
      val vvhRoadLink = vvhRoadLinks.find(_.mmlId == roadLink.mmlId)
      vvhRoadLink.get.featureClass match {
        case FeatureClass.TractorRoad => roadLink.copy(functionalClass = 7, linkType = TractorRoad)
        case FeatureClass.DrivePath => roadLink.copy(functionalClass = 6, linkType = SingleCarriageway)
        case FeatureClass.CycleOrPedestrianPath => roadLink.copy(functionalClass = 8, linkType = CycleOrPedestrianPath)
        case _ => roadLink
      }
    }
    def toIncompleteLink(roadLink: VVHRoadLinkWithProperties): IncompleteLink = {
      val vvhRoadLink = vvhRoadLinks.find(_.mmlId == roadLink.mmlId)
      IncompleteLink(roadLink.mmlId, vvhRoadLink.get.municipalityCode, roadLink.administrativeClass)
    }

    def isIncomplete(roadLink: VVHRoadLinkWithProperties): Boolean = {
      roadLink.functionalClass == FunctionalClass.Unknown || roadLink.linkType.value == UnknownLinkType.value
    }
    def canBeAutoGenerated(roadLink: VVHRoadLinkWithProperties): Boolean = {
      vvhRoadLinks.find(_.mmlId == roadLink.mmlId).get.featureClass match {
        case FeatureClass.AllOthers => false
        case _ => true
      }
    }

    val roadLinkDataByMmlId: Seq[VVHRoadLinkWithProperties] = getRoadLinkDataByMmlIds(vvhRoadLinks)
    val (incompleteLinks, completeLinks) = roadLinkDataByMmlId.partition(isIncomplete)
    val (linksToAutoGenerate, incompleteOtherLinks) = incompleteLinks.partition(canBeAutoGenerated)
    val autoGeneratedLinks = linksToAutoGenerate.map(autoGenerateProperties)

    eventbus.publish("linkProperties:changed",
      RoadLinkChangeSet(autoGeneratedLinks, incompleteOtherLinks.map(toIncompleteLink)))

    completeLinks ++ autoGeneratedLinks ++ incompleteOtherLinks
  }

  def fetchVVHRoadlinks(municipalityCode: Int): Seq[VVHRoadlink]

  def fetchVVHRoadlinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[VVHRoadlink]

  def fetchVVHRoadlink(mmlId: Long): Option[VVHRoadlink]
  def fetchVVHRoadlinks(mmlIds: Set[Long]): Seq[VVHRoadlink]
  def fetchVVHRoadlinks[T](mmlIds: Set[Long],
                           fieldSelection: Option[String],
                           fetchGeometry: Boolean,
                           resultTransition: (Map[String, Any], List[List[Double]]) => T): Seq[T]

  def getIncompleteLinks(includedMunicipalities: Option[Set[Int]]): Map[String, Map[String, Seq[Long]]]

  def getRoadLinkDataByMmlIds(vvhRoadLinks: Seq[VVHRoadlink]): Seq[VVHRoadLinkWithProperties] = {
    val basicRoadLinks = vvhRoadLinks.map { roadLink =>
      BasicRoadLink(0, roadLink.mmlId, roadLink.geometry, GeometryUtils.geometryLength(roadLink.geometry), roadLink.administrativeClass, roadLink.trafficDirection, roadLink.modifiedAt)
    }
    val adjustedLinks = adjustedRoadLinks(basicRoadLinks)
    adjustedLinks.map{ link => VVHRoadLinkWithProperties(link.mmlId, link.geometry,
      link.length, link.administrativeClass, link.functionalClass, link.trafficDirection,
      LinkType(link.linkType), link.modifiedAt, link.modifiedBy, attributes = vvhRoadLinks.find(_.mmlId==link.mmlId).get.attributes) }
  }

  def getAdjacent(mmlId: Long): Seq[VVHRoadLinkWithProperties] = {
    val endpoints = getRoadLinkGeometry(mmlId).map(GeometryUtils.geometryEndpoints)
    endpoints.map(endpoint => {
      val delta: Vector3d = Vector3d(0.1, 0.1, 0)
      val bounds = BoundingRectangle(endpoint._1 - delta, endpoint._1 + delta)
      val bounds2 = BoundingRectangle(endpoint._2 - delta, endpoint._2 + delta)
      val roadLinks = getRoadLinksFromVVH(bounds) ++ getRoadLinksFromVVH(bounds2)
      roadLinks.filterNot(_.mmlId == mmlId)
        .filter(roadLink => roadLink.isCarTrafficRoad)
        .filter(roadLink => {
        val geometry = roadLink.geometry
        val epsilon = 0.01
        val rlEndpoints = GeometryUtils.geometryEndpoints(geometry)
        rlEndpoints._1.distanceTo(endpoint._1) < epsilon ||
          rlEndpoints._2.distanceTo(endpoint._1) < epsilon ||
          rlEndpoints._1.distanceTo(endpoint._2) < epsilon ||
          rlEndpoints._2.distanceTo(endpoint._2) < epsilon
      })
    }).getOrElse(Nil)
  }
}

object RoadLinkService extends RoadLinkService {
  override val eventbus = new DummyEventBus

  override def fetchVVHRoadlinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set()) = {
    throw new NotImplementedError()
  }

  override def fetchVVHRoadlink(mmlId: Long): Option[VVHRoadlink] = {
    throw new NotImplementedError()
  }

  override def fetchVVHRoadlinks(municipalityCode: Int) = throw new NotImplementedError()

  override def getIncompleteLinks(includedMunicipalities: Option[Set[Int]]): Map[String, Map[String, Seq[Long]]] = throw new NotImplementedError()

  override def getRoadLinkMiddlePointByMMLId(mmlId: Long): Option[(Long, Point)] = throw new NotImplementedError()

  override def fetchVVHRoadlinks(mmlIds: Set[Long]) = throw new NotImplementedError
  override def fetchVVHRoadlinks[T](mmlIds: Set[Long],
                                    fieldSelection: Option[String],
                                    fetchGeometry: Boolean,
                                    resultTransition: (Map[String, Any], List[List[Double]]) => T): Seq[T] = throw new NotImplementedError
  override def updateProperties(id: Long, functionalClass: Int, linkType: LinkType,
                                direction: TrafficDirection, username: String, municipalityValidation: Int => Unit): Option[VVHRoadLinkWithProperties] = throw new NotImplementedError()
}

class VVHRoadLinkService(vvhClient: VVHClient, val eventbus: DigiroadEventBus) extends RoadLinkService {
  override def fetchVVHRoadlinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[VVHRoadlink] = {
    vvhClient.fetchVVHRoadlinks(bounds, municipalities)
  }

  override def fetchVVHRoadlink(mmlId: Long): Option[VVHRoadlink] = {
    vvhClient.fetchVVHRoadlink(mmlId)
  }

  override def fetchVVHRoadlinks(mmlIds: Set[Long]): Seq[VVHRoadlink] = {
    if (mmlIds.nonEmpty) vvhClient.fetchVVHRoadlinks(mmlIds)
    else Seq.empty[VVHRoadlink]
  }
  override def fetchVVHRoadlinks[T](mmlIds: Set[Long],
                                    fieldSelection: Option[String],
                                    fetchGeometry: Boolean,
                                    resultTransition: (Map[String, Any], List[List[Double]]) => T): Seq[T] = {
    if (mmlIds.nonEmpty) vvhClient.fetchVVHRoadlinks(mmlIds, fieldSelection, fetchGeometry, resultTransition)
    else Seq.empty[T]
  }
  override def fetchVVHRoadlinks(municipalityCode: Int): Seq[VVHRoadlink] = {
    vvhClient.fetchByMunicipality(municipalityCode)
  }

  override def getIncompleteLinks(includedMunicipalities: Option[Set[Int]]): Map[String, Map[String, Seq[Long]]] = {
    case class IncompleteLink(mmlId: Long, municipality: String, administrativeClass: String)
    def toIncompleteLink(x: (Long, String, Int)) = IncompleteLink(x._1, x._2, AdministrativeClass(x._3).toString)

    withDynSession {
      val optionalMunicipalities = includedMunicipalities.map(_.mkString(","))
      val incompleteLinksQuery = """
        select l.mml_id, m.name_fi, l.administrative_class
        from incomplete_link l
        join municipality m on l.municipality_code = m.id
      """

      val sql = optionalMunicipalities match {
        case Some(municipalities) => incompleteLinksQuery + s" where l.municipality_code in ($municipalities)"
        case _ => incompleteLinksQuery
      }

      Q.queryNA[(Long, String, Int)](sql).list
        .map(toIncompleteLink)
        .groupBy(_.municipality)
        .mapValues { _.groupBy(_.administrativeClass)
                      .mapValues(_.map(_.mmlId)) }
    }
  }

  override def getRoadLinkMiddlePointByMMLId(mmlId: Long): Option[(Long, Point)] = {
    val middlePoint: Option[Point] = vvhClient.fetchVVHRoadlink(mmlId)
      .flatMap { vvhRoadLink =>
      GeometryUtils.calculatePointFromLinearReference(vvhRoadLink.geometry, GeometryUtils.geometryLength(vvhRoadLink.geometry) / 2.0)
    }
    middlePoint.map((mmlId, _))
  }

  override def updateProperties(mmlId: Long, functionalClass: Int, linkType: LinkType,
                                direction: TrafficDirection, username: String, municipalityValidation: Int => Unit): Option[VVHRoadLinkWithProperties] = {
    val vvhRoadLink = fetchVVHRoadlink(mmlId)
    vvhRoadLink.map { vvhRoadLink =>
      municipalityValidation(vvhRoadLink.municipalityCode)
      withDynTransaction {
        setLinkProperty("traffic_direction", "traffic_direction", direction.value, mmlId, username, Some(vvhRoadLink.trafficDirection.value))
        if (functionalClass != FunctionalClass.Unknown) setLinkProperty("functional_class", "functional_class", functionalClass, mmlId, username)
        if (linkType != UnknownLinkType) setLinkProperty("link_type", "link_type", linkType.value, mmlId, username)
        val enrichedLink = enrichRoadLinksFromVVH(Seq(vvhRoadLink)).head
        if (enrichedLink.functionalClass != FunctionalClass.Unknown && enrichedLink.linkType != UnknownLinkType) {
          removeIncompleteness(mmlId)
        }
        enrichedLink
      }
    }
  }
}
