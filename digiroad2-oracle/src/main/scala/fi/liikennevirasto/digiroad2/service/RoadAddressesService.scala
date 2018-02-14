package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.asset.{PropertyTypes, PropertyValue, Property, CycleOrPedestrianPath}
import fi.liikennevirasto.digiroad2.client.vvh.FeatureClass.TractorRoad
import fi.liikennevirasto.digiroad2.dao.{RoadAddress, RoadAddressDAO}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLinkLike, RoadLink}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.{Point, DigiroadEventBus, GeometryUtils}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

case class ChangedRoadAddress(roadAddress : RoadAddress, link: RoadLink)

class RoadAddressesService(val eventbus: DigiroadEventBus, roadLinkServiceImplementation: RoadLinkService) {

  private val roadNumberPublicId = "tie"          // Tienumero
  private val roadPartNumberPublicId = "osa"      // Tieosanumero
  private val startMeasurePublicId = "aet"        // Etaisyys
  private val trackCodePublicId = "ajr"           // Ajorata
  private val sideCodePublicId = "puoli"

  val roadAddressDAO = new RoadAddressDAO()
  val logger = LoggerFactory.getLogger(getClass)

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  def getChanged(sinceDate: DateTime, untilDate: DateTime): Seq[ChangedRoadAddress] = {

    val roadAddresses =
      withDynTransaction {
        roadAddressDAO.getRoadAddress(roadAddressDAO.withBetweenDates(sinceDate, untilDate))
      }

    val roadLinks = roadLinkServiceImplementation.getRoadLinksAndComplementariesFromVVH(roadAddresses.map(_.linkId).toSet)
    val roadLinksWithoutWalkways = roadLinks.filterNot(_.linkType == CycleOrPedestrianPath).filterNot(_.linkType == TractorRoad)

    roadAddresses.flatMap { roadAddress =>
      roadLinksWithoutWalkways.find(_.linkId == roadAddress.linkId).map { roadLink =>
        ChangedRoadAddress(
          roadAddress = RoadAddress(
            id = roadAddress.id,
            roadNumber = roadAddress.roadNumber,
            roadPartNumber = roadAddress.roadPartNumber,
            track = roadAddress.track,
            discontinuity = roadAddress.discontinuity,
            startAddrMValue = roadAddress.startAddrMValue,
            endAddrMValue = roadAddress.endAddrMValue,
            startDate = roadAddress.startDate,
            endDate = roadAddress.endDate,
            lrmPositionId = roadAddress.lrmPositionId,
            linkId = roadAddress.linkId,
            startMValue = roadAddress.startMValue,
            endMValue = roadAddress.endMValue,
            sideCode = roadAddress.sideCode,
            floating = roadAddress.floating,
            geom = GeometryUtils.truncateGeometry3D(roadLink.geometry, roadAddress.startMValue, roadAddress.endMValue),
            expired = roadAddress.expired,
            createdBy = roadAddress.createdBy,
            createdDate = roadAddress.createdDate,
            modifiedDate = roadAddress.modifiedDate
          ),
          link = roadLink
        )
      }
    }
  }

  def getRoadAddressPropertiesByLinkId(assetCoordinates: Point, linkId: Long, roadLink: RoadLinkLike, oldProperties: Seq[Property]): Seq[Property] = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(assetCoordinates, roadLink.geometry)
    roadAddressDAO.getByLinkId(linkId, mValue, mValue).flatMap { ra =>
      val addrMValue = ra.addrAt(mValue).toString()

      val newRoadAddressProperties =
        Seq(
          new Property(0, roadNumberPublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(ra.roadNumber.toString(), Some(ra.roadNumber.toString())))),
          new Property(0, roadPartNumberPublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(ra.roadPartNumber.toString(), Some(ra.roadPartNumber.toString())))),
          new Property(0, startMeasurePublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(addrMValue, Some(addrMValue)))),
          new Property(0, trackCodePublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(ra.track.value.toString(), Some(ra.track.value.toString())))),
          new Property(0, sideCodePublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(ra.sideCode.value.toString(), Some(ra.sideCode.value.toString()))))
        )

      return oldProperties.filterNot(op => newRoadAddressProperties.map(_.publicId).contains(op.publicId)) ++ newRoadAddressProperties
    }

    oldProperties
  }

}
