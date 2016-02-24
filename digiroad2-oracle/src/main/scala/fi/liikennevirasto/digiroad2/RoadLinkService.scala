package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.GeometryUtils._
import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

case class IncompleteLink(linkId: Long, municipalityCode: Int, administrativeClass: AdministrativeClass)
case class RoadLinkChangeSet(adjustedRoadLinks: Seq[RoadLink], incompleteLinks: Seq[IncompleteLink])
case class LinkProperties(linkId: Long, functionalClass: Int, linkType: LinkType, trafficDirection: TrafficDirection)

class RoadLinkService(val vvhClient: VVHClient, val eventbus: DigiroadEventBus) {
  val logger = LoggerFactory.getLogger(getClass)

  def fetchVVHRoadlinks(linkIds: Set[Long]): Seq[VVHRoadlink] = {
    if (linkIds.nonEmpty) vvhClient.fetchVVHRoadlinks(linkIds)
    else Seq.empty[VVHRoadlink]
  }

  def fetchVVHRoadlinks[T](linkIds: Set[Long],
                                    fieldSelection: Option[String],
                                    fetchGeometry: Boolean,
                                    resultTransition: (Map[String, Any], List[List[Double]]) => T): Seq[T] = {
    if (linkIds.nonEmpty) vvhClient.fetchVVHRoadlinks(linkIds, fieldSelection, fetchGeometry, resultTransition)
    else Seq.empty[T]
  }

  def fetchVVHRoadlinks(municipalityCode: Int): Seq[VVHRoadlink] = {
    vvhClient.fetchByMunicipality(municipalityCode)
  }

  def getIncompleteLinks(includedMunicipalities: Option[Set[Int]]): Map[String, Map[String, Seq[Long]]] = {
    case class IncompleteLink(linkId: Long, municipality: String, administrativeClass: String)
    def toIncompleteLink(x: (Long, String, Int)) = IncompleteLink(x._1, x._2, AdministrativeClass(x._3).toString)

    withDynSession {
      val optionalMunicipalities = includedMunicipalities.map(_.mkString(","))
      val incompleteLinksQuery = """
        select l.link_id, m.name_fi, l.administrative_class
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
          .mapValues(_.map(_.linkId)) }
    }
  }

  def getRoadLinkMiddlePointByLinkId(linkId: Long): Option[(Long, Point)] = {
    val middlePoint: Option[Point] = vvhClient.fetchVVHRoadlink(linkId)
      .flatMap { vvhRoadLink =>
        GeometryUtils.calculatePointFromLinearReference(vvhRoadLink.geometry, GeometryUtils.geometryLength(vvhRoadLink.geometry) / 2.0)
      }
    middlePoint.map((linkId, _))
  }

  def getRoadLinkMiddlePointByMmlId(mmlId: Long): Option[(Long, Point)] = {
    vvhClient.fetchVVHRoadlinkByMmlId(mmlId).flatMap { vvhRoadLink =>
      val point = GeometryUtils.calculatePointFromLinearReference(vvhRoadLink.geometry, GeometryUtils.geometryLength(vvhRoadLink.geometry) / 2.0)
      point match {
        case Some(point) => Some(vvhRoadLink.linkId, point)
        case None => None
      }
    }
  }

  def updateProperties(linkId: Long, functionalClass: Int, linkType: LinkType,
                                direction: TrafficDirection, username: String, municipalityValidation: Int => Unit): Option[RoadLink] = {
    val vvhRoadLink = vvhClient.fetchVVHRoadlink(linkId)
    vvhRoadLink.map { vvhRoadLink =>
      municipalityValidation(vvhRoadLink.municipalityCode)
      withDynTransaction {
        setLinkProperty("traffic_direction", "traffic_direction", direction.value, linkId, username, Some(vvhRoadLink.trafficDirection.value))
        if (functionalClass != FunctionalClass.Unknown) setLinkProperty("functional_class", "functional_class", functionalClass, linkId, username)
        if (linkType != UnknownLinkType) setLinkProperty("link_type", "link_type", linkType.value, linkId, username)
        val enrichedLink = enrichRoadLinksFromVVH(Seq(vvhRoadLink)).head
        if (enrichedLink.functionalClass != FunctionalClass.Unknown && enrichedLink.linkType != UnknownLinkType) {
          removeIncompleteness(linkId)
        }
        enrichedLink
      }
    }
  }
  
  def getRoadLinkGeometry(id: Long): Option[Seq[Point]] = {
    vvhClient.fetchVVHRoadlink(id).map(_.geometry)
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

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  private def updateExistingLinkPropertyRow(table: String, column: String, linkId: Long, username: String, existingValue: Int, value: Int) = {
    if (existingValue != value) {
      sqlu"""update #$table
               set #$column = $value,
                   modified_date = current_timestamp,
                   modified_by = $username
               where link_id = $linkId""".execute
    }
  }

  protected def setLinkProperty(table: String, column: String, value: Int, linkId: Long, username: String, optionalVVHValue: Option[Int] = None) = {
    val optionalExistingValue: Option[Int] = sql"""select #$column from #$table where link_id = $linkId""".as[Int].firstOption
    (optionalExistingValue, optionalVVHValue) match {
      case (Some(existingValue), _) =>
        updateExistingLinkPropertyRow(table, column, linkId, username, existingValue, value)
      case (None, None) =>
        sqlu"""insert into #$table (id, link_id, #$column, modified_by)
                 select primary_key_seq.nextval, $linkId, $value, $username
                 from dual
                 where not exists (select * from #$table where link_id = $linkId)""".execute
      case (None, Some(vvhValue)) =>
        if (vvhValue != value)
          sqlu"""insert into #$table (id, link_id, #$column, modified_by)
                   select primary_key_seq.nextval, $linkId, $value, $username
                   from dual
                   where not exists (select * from #$table where link_id = $linkId)""".execute
    }
  }

  implicit val getDateTime = new GetResult[DateTime] {
    def apply(r: PositionedResult) = {
      new DateTime(r.nextTimestamp())
    }
  }

  private def fetchTrafficDirections(idTableName: String): Seq[(Long, Int, DateTime, String)] = {
    sql"""select t.link_id, t.traffic_direction, t.modified_date, t.modified_by
            from traffic_direction t
            join #$idTableName i on i.id = t.link_id""".as[(Long, Int, DateTime, String)].list
  }

  private def fetchFunctionalClasses(idTableName: String): Seq[(Long, Int, DateTime, String)] = {
    sql"""select f.link_id, f.functional_class, f.modified_date, f.modified_by
            from functional_class f
            join #$idTableName i on i.id = f.link_id""".as[(Long, Int, DateTime, String)].list
  }

  private def fetchLinkTypes(idTableName: String): Seq[(Long, Int, DateTime, String)] = {
    sql"""select l.link_id, l.link_type, l.modified_date, l.modified_by
            from link_type l
            join #$idTableName i on i.id = l.link_id""".as[(Long, Int, DateTime, String)].list
  }

  private def adjustedRoadLinks(vvhRoadlinks: Seq[VVHRoadlink]): Seq[RoadLink] = {
    val (adjustedTrafficDirections, adjustedFunctionalClasses, adjustedLinkTypes) =
      MassQuery.withIds(vvhRoadlinks.map(_.linkId).toSet) { idTableName =>
        val trafficDirections: Map[Long, Seq[(Long, Int, DateTime, String)]] = fetchTrafficDirections(idTableName).groupBy(_._1)
        val functionalClasses: Map[Long, Seq[(Long, Int, DateTime, String)]] = fetchFunctionalClasses(idTableName).groupBy(_._1)
        val linkTypes: Map[Long, Seq[(Long, Int, DateTime, String)]] = fetchLinkTypes(idTableName).groupBy(_._1)
        (trafficDirections, functionalClasses, linkTypes)
      }

    vvhRoadlinks.map { link =>
      val linkId = link.linkId
      val functionalClass = adjustedFunctionalClasses.get(linkId).flatMap(_.headOption)
      val adjustedLinkType = adjustedLinkTypes.get(linkId).flatMap(_.headOption)
      val trafficDirection = adjustedTrafficDirections.get(linkId).flatMap(_.headOption)

      val functionalClassValue = functionalClass.map(_._2).getOrElse(FunctionalClass.Unknown)
      val adjustedLinkTypeValue = adjustedLinkType.map(_._2).getOrElse(UnknownLinkType.value)
      val trafficDirectionValue = trafficDirection.map(trafficDirection =>
        TrafficDirection(trafficDirection._2)
      ).getOrElse(link.trafficDirection)

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
      } :+ link.modifiedAt.map(at => (at, "vvh"))

      val modifics: Option[(DateTime, String)] = modifications.reduce(latestModifications)
      val (modifiedAt, modifiedBy) = (modifics.map(_._1), modifics.map(_._2))

      RoadLink(link.linkId, link.geometry,
        GeometryUtils.geometryLength(link.geometry), link.administrativeClass, functionalClassValue, trafficDirectionValue,
        LinkType(adjustedLinkTypeValue), modifiedAt.map(DateTimePropertyFormat.print), modifiedBy, attributes = link.attributes)
    }
  }

  def getRoadLinksFromVVH(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[RoadLink] = {
    // todo: store result, for now siply call vvh and print debug

    val (changes, links) = Await.result(vvhClient.fetchChangesF(bounds).zip(vvhClient.fetchVVHRoadlinksF(bounds, municipalities)), atMost = Duration.Inf)

    withDynTransaction {
      enrichRoadLinksFromVVH(links, changes)
    }
  }

  def getVVHRoadLinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[VVHRoadlink] = {
    vvhClient.fetchVVHRoadlinks(bounds, municipalities)
  }
  def getVVHRoadLinks(bounds: BoundingRectangle): Seq[VVHRoadlink] = {
    vvhClient.fetchVVHRoadlinks(bounds)
  }

  def getRoadLinksFromVVH(linkIds: Set[Long]): Seq[RoadLink] = {
    val vvhRoadLinks = fetchVVHRoadlinks(linkIds)
    withDynTransaction {
      enrichRoadLinksFromVVH(vvhRoadLinks)
    }
  }

  def getRoadLinksFromVVH(municipality: Int): Seq[RoadLink] = {
    val vvhRoadLinks = fetchVVHRoadlinks(municipality)
    withDynTransaction {
      enrichRoadLinksFromVVH(vvhRoadLinks)
    }
  }

  def getRoadLinkFromVVH(linkId: Long): Option[RoadLink] = {
    val vvhRoadLinks = fetchVVHRoadlinks(Set(linkId))
    withDynTransaction {
      enrichRoadLinksFromVVH(vvhRoadLinks)
    }.headOption
  }

  def getClosestRoadlinkFromVVH(user: User, point: Point): Option[VVHRoadlink] = {
    val diagonal = Vector3d(500, 500, 0)

    val roadLinks =
      if (user.isOperator())
        getVVHRoadLinks(BoundingRectangle(point - diagonal, point + diagonal))
      else
        getVVHRoadLinks(BoundingRectangle(point - diagonal, point + diagonal), user.configuration.authorizedMunicipalities)

    if (roadLinks.isEmpty)
      None
    else
      Some(roadLinks.minBy(roadlink => minimumDistance(point, roadlink.geometry)))
  }

  protected def removeIncompleteness(linkId: Long) = {
    sqlu"""delete from incomplete_link where link_id = $linkId""".execute
  }

  def updateRoadLinkChanges(roadLinkChangeSet: RoadLinkChangeSet): Unit = {
    updateAutoGeneratedProperties(roadLinkChangeSet.adjustedRoadLinks)
    updateIncompleteLinks(roadLinkChangeSet.incompleteLinks)
  }

  def updateAutoGeneratedProperties(adjustedRoadLinks: Seq[RoadLink]) {
    def updateProperties(roadLink: RoadLink) = {
      setLinkProperty("functional_class", "functional_class", roadLink.functionalClass, roadLink.linkId, "automatic_generation")
      setLinkProperty("link_type", "link_type", roadLink.linkType.value, roadLink.linkId, "automatic_generation")
    }
    withDynTransaction {
      adjustedRoadLinks.foreach(updateProperties)
      adjustedRoadLinks.foreach(link => removeIncompleteness(link.linkId))
    }
  }

  protected def updateIncompleteLinks(incompleteLinks: Seq[IncompleteLink]) = {
    def setIncompleteness(incompleteLink: IncompleteLink) {
      withDynTransaction {
        sqlu"""insert into incomplete_link(id, link_id, municipality_code, administrative_class)
                 select primary_key_seq.nextval, ${incompleteLink.linkId}, ${incompleteLink.municipalityCode}, ${incompleteLink.administrativeClass.value} from dual
                 where not exists (select * from incomplete_link where link_id = ${incompleteLink.linkId})""".execute
      }
    }
    incompleteLinks.foreach(setIncompleteness)
  }

  // fill incomplete links with the previous link information where they are available and where they agree
  def fillIncompleteLinksWithPreviousLinkData(incompleteLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): (Seq[RoadLink], Seq[RoadLink]) = {
    val sourceRoadLinkAttributes = getOldRoadLinkAttributesFromChangeInfo(changes)

    incompleteLinks.map { link =>
      val sourceIds = changes.filter(change => change.newId == Option(link.linkId)).map(_.oldId).flatten
      val sourceData = sourceRoadLinkAttributes.filter(link => sourceIds.contains(link.linkId))

      if (!sourceData.isEmpty) {
        val newFunctionalClass = if (sourceData.forall(_.functionalClass == sourceData.head.functionalClass)) sourceData.head.functionalClass else FunctionalClass.Unknown
        val newLinkType = if (sourceData.forall(_.linkType == sourceData.head.linkType)) sourceData.head.linkType else UnknownLinkType
        val newTrafficDirection = if (sourceData.forall(_.trafficDirection == sourceData.head.trafficDirection)) sourceData.head.trafficDirection else link.trafficDirection

        link.copy(functionalClass = newFunctionalClass, linkType = newLinkType, trafficDirection = newTrafficDirection)
      } else {
        link
      }
    }.partition(isComplete)
  }

  def isComplete(roadLink: RoadLink): Boolean = {
    roadLink.functionalClass != FunctionalClass.Unknown && roadLink.linkType.value != UnknownLinkType.value
  }
  def isIncomplete(roadLink: RoadLink): Boolean = !isComplete(roadLink)

  protected def enrichRoadLinksFromVVH(vvhRoadLinks: Seq[VVHRoadlink], changes: Seq[ChangeInfo] = Nil): Seq[RoadLink] = {
    def autoGenerateProperties(roadLink: RoadLink): RoadLink = {
      val vvhRoadLink = vvhRoadLinks.find(_.linkId == roadLink.linkId)
      vvhRoadLink.get.featureClass match {
        case FeatureClass.TractorRoad => roadLink.copy(functionalClass = 7, linkType = TractorRoad)
        case FeatureClass.DrivePath => roadLink.copy(functionalClass = 6, linkType = SingleCarriageway)
        case FeatureClass.CycleOrPedestrianPath => roadLink.copy(functionalClass = 8, linkType = CycleOrPedestrianPath)
        case _ => roadLink
      }
    }
    def toIncompleteLink(roadLink: RoadLink): IncompleteLink = {
      val vvhRoadLink = vvhRoadLinks.find(_.linkId == roadLink.linkId)
      IncompleteLink(roadLink.linkId, vvhRoadLink.get.municipalityCode, roadLink.administrativeClass)
    }

    def canBeAutoGenerated(roadLink: RoadLink): Boolean = {
      vvhRoadLinks.find(_.linkId == roadLink.linkId).get.featureClass match {
        case FeatureClass.AllOthers => false
        case _ => true
      }
    }

    val roadLinkDataByLinkId: Seq[RoadLink] = getRoadLinkDataByLinkIds(vvhRoadLinks)
    val (incompleteLinks, completeLinks) = roadLinkDataByLinkId.partition(isIncomplete)
    val (linksToAutoGenerate, incompleteOtherLinks) = incompleteLinks.partition(canBeAutoGenerated)
    val autoGeneratedLinks = linksToAutoGenerate.map(autoGenerateProperties)
    val (changedLinks, stillIncompleteLinks) = fillIncompleteLinksWithPreviousLinkData(incompleteOtherLinks, changes)

    // todo: handle cases where only one of functional class or link type is found for old link
    //       -> should do two things: persist the found information and add to incomplete list
    eventbus.publish("linkProperties:changed",
      RoadLinkChangeSet(autoGeneratedLinks ++ changedLinks, stillIncompleteLinks.map(toIncompleteLink)))

    completeLinks ++ autoGeneratedLinks ++ changedLinks ++ stillIncompleteLinks
  }

  def getOldRoadLinkAttributesFromChangeInfo(changes: Seq[ChangeInfo]): Seq[RoadLink] = {
    val vvhLinks = changes.map(_.oldId).flatten.distinct.map { oldLinkId =>
      VVHRoadlink(oldLinkId, 0, Nil, Unknown, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
    }
    getRoadLinkDataByLinkIds(vvhLinks)
  }

  def getRoadLinkDataByLinkIds(vvhRoadLinks: Seq[VVHRoadlink]): Seq[RoadLink] = {
    adjustedRoadLinks(vvhRoadLinks)
  }

  def getAdjacent(linkId: Long): Seq[RoadLink] = {
    val sourceLinkGeometryOption = getRoadLinkGeometry(linkId)
    sourceLinkGeometryOption.map(sourceLinkGeometry => {
      val sourceLinkEndpoints = GeometryUtils.geometryEndpoints(sourceLinkGeometry)
      val delta: Vector3d = Vector3d(0.1, 0.1, 0)
      val bounds = BoundingRectangle(sourceLinkEndpoints._1 - delta, sourceLinkEndpoints._1 + delta)
      val bounds2 = BoundingRectangle(sourceLinkEndpoints._2 - delta, sourceLinkEndpoints._2 + delta)
      val roadLinks = getRoadLinksFromVVH(bounds) ++ getRoadLinksFromVVH(bounds2)
      roadLinks.filterNot(_.linkId == linkId)
        .filter(roadLink => roadLink.isCarTrafficRoad)
        .filter(roadLink => {
        val targetLinkGeometry = roadLink.geometry
        GeometryUtils.areAdjacent(sourceLinkGeometry, targetLinkGeometry)
      })
    }).getOrElse(Nil)
  }
}


