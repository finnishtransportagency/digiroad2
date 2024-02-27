package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.postgis.MassQuery
import fi.liikennevirasto.digiroad2.util.MatchedRoadLinks
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult}

case class RoadLinkReplacementWorkListItem(id: Long, removedLinkId: String, addedLinkId: String)

class RoadLinkReplacementWorkListDAO {

  implicit val getItemResult: GetResult[RoadLinkReplacementWorkListItem] = new GetResult[RoadLinkReplacementWorkListItem] {
    def apply(result: PositionedResult): RoadLinkReplacementWorkListItem = {
      val id = result.nextLong()
      val removedLinkId = result.nextString()
      val addedLinkId = result.nextString()

      RoadLinkReplacementWorkListItem(id,removedLinkId, addedLinkId)
    }
  }

  def insertMatchedLinksToWorkList(matchedRoadLinks: Seq[MatchedRoadLinks]): Unit = {
    val ids = Sequences.nextPrimaryKeySeqValues(matchedRoadLinks.size)
    val matchedRoadLinksWithIds = matchedRoadLinks.zipWithIndex.map { case (matchedLinks, index) =>
      (matchedLinks, ids(index))
    }

    val insertMatchedLinks =
      s"""insert into road_link_replacement_work_list (id, removed_link_id, added_link_id) values ((?), (?), (?))""".stripMargin

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

  def getMatchedRoadLinksWorkList(): Seq[RoadLinkReplacementWorkListItem] = {
    sql"""SELECT id, removed_link_id, added_link_id FROM road_link_replacement_work_list""".as[RoadLinkReplacementWorkListItem].list
  }

  def deleteFromWorkList(idsToDelete: Set[Long]) = {
    sqlu"""DELETE FROM road_link_replacement_work_list WHERE id IN (#${idsToDelete.mkString(",")})""".execute
  }

}
