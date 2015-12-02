package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.VVHRoadLinkWithProperties
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

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
