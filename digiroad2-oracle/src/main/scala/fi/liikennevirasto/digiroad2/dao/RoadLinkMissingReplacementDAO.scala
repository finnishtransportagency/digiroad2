package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.postgis.MassQuery
import fi.liikennevirasto.digiroad2.util.MatchedRoadLinks
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult}

case class MatchedRoadLinkWorkListItem(id: Long, removedLinkId: String, addedLinkId: String, hausdorffSimilarityMeasure: Double, areaSimilarityMeasure: Double)

class RoadLinkMissingReplacementDAO {

  implicit val getItemResult: GetResult[MatchedRoadLinkWorkListItem] = new GetResult[MatchedRoadLinkWorkListItem] {
    def apply(result: PositionedResult): MatchedRoadLinkWorkListItem = {
      val id = result.nextLong()
      val removedLinkId = result.nextString()
      val addedLinkId = result.nextString()
      val hausdorffSimilarityMeasure = result.nextDouble()
      val areaSimilarityMeasure = result.nextDouble()

      MatchedRoadLinkWorkListItem(id,removedLinkId, addedLinkId, hausdorffSimilarityMeasure, areaSimilarityMeasure)
    }
  }

  def insertMatchedLinksToWorkList(matchedRoadLinks: Seq[MatchedRoadLinks]): Unit = {
    val ids = Sequences.nextPrimaryKeySeqValues(matchedRoadLinks.size)
    val matchedRoadLinksWithIds = matchedRoadLinks.zipWithIndex.map { case (matchedLinks, index) =>
      (matchedLinks, ids(index))
    }

    val insertMatchedLinks =
      s"""insert into matched_road_links_work_list (id, removed_link_id, added_link_id) values ((?), (?), (?))""".stripMargin

    MassQuery.executeBatch(insertMatchedLinks) { statement =>
      matchedRoadLinksWithIds.foreach { mlWithId =>
        val (matchedLinks, id) = mlWithId
        statement.setLong(1, id)
        statement.setString(2, matchedLinks.removedLinkId)
        statement.setString(3, matchedLinks.addedLinkId)
        statement.addBatch()
      }
    }
  }

  def getMatchedRoadLinksWorkList(): Seq[MatchedRoadLinkWorkListItem] = {
    sql"""SELECT id, removed_link_id, added_link_id, hausdorff_similarity_measure, area_similarity_measure FROM matched_road_links_work_list""".as[MatchedRoadLinkWorkListItem].list
  }

  def deleteFromWorkList(idsToDelete: Seq[Long]) = {
    sqlu"""DELETE FROM matched_road_links_work_list WHERE id IN (#${idsToDelete.mkString(",")})""".execute
  }

}
