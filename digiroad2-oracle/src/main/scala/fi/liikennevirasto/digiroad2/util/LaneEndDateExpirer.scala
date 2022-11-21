package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.DummySerializer
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.util.LaneUtils.{eventbus, roadAddressService}
import org.joda.time.DateTime
import org.slf4j.{Logger, LoggerFactory}

object LaneEndDateExpirer {
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  val logger: Logger = LoggerFactory.getLogger(getClass)
  lazy val laneService: LaneService = new LaneService(roadLinkService, eventbus, roadAddressService)
  lazy val roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, eventbus, new DummySerializer)
  lazy val roadLinkClient: RoadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  lazy val username = "LaneEndDateExpirer"

  def checkForExpires(): Unit = {
    withDynTransaction {
      val municipalities = Queries.getMunicipalities
      municipalities.foreach(municipality => {
        val dateTimeNow = DateTime.now()
        val lanesInMunicipality = laneService.getLanesByMunicipality(municipality, newTransaction = false)

        val lanesToExpire = lanesInMunicipality.filter(lane => {
          val endDate = lane.attributes.find(_.publicId == "end_date")
          endDate match {
            case Some(ed) =>
              val endDate = ed.values.head
              val endDateTime = new DateTime(endDate)
              endDateTime.isBefore(dateTimeNow)
            case None => false
          }
        })

        LogUtils.time(logger, "Expiring " + lanesToExpire.size + " lanes in municipality" + municipality) {
          lanesToExpire.foreach(lane => {
            logger.info("Expiring lane ID: " + lane.id + "linkID: " + lane.linkId)
            laneService.moveToHistory(lane.id, None, expireHistoryLane = true, deleteFromLanes = true, username)
          })
        }
      })
    }
  }

}
