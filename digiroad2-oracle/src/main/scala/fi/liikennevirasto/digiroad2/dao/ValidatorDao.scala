package fi.liikennevirasto.digiroad2.dao
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.LinearReference
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

sealed case class LinearReferenceAsset(assetId:Long,lrm:LinearReference,laneCode:Option[Int]=None)

trait ValidatorDao {
  val filterLink = (column: String, links: Set[String]) => if (links.nonEmpty) s" and ${column} in (${links.map(t => s"'${t}'").mkString(",")})" else ""

  implicit val getResult: GetResult[LinearReferenceAsset] = new GetResult[LinearReferenceAsset] {
    def apply(r: PositionedResult): LinearReferenceAsset = {
      val assetId = r.nextLong()
      val linkId = r.nextString()
      val sideCode: Option[Int] = r.nextIntOption()
      val startMValue: Double = r.nextDouble()
      val endMValue: Option[Double] = r.nextDoubleOption()
      LinearReferenceAsset(assetId, LinearReference(linkId, startMValue, endMValue, sideCode))
    }
  }
  implicit val getResultLane: GetResult[LinearReferenceAsset] = new GetResult[LinearReferenceAsset] {
    def apply(r: PositionedResult): LinearReferenceAsset = {
      val assetId = r.nextLong()
      val laneCode = r.nextIntOption()
      val linkId = r.nextString()
      val sideCode: Option[Int] = r.nextIntOption()
      val startMValue: Double = r.nextDouble()
      val endMValue: Option[Double] = r.nextDoubleOption()
      LinearReferenceAsset(assetId, LinearReference(linkId, startMValue, endMValue, sideCode),laneCode)
    }
  }
}
object ValidatorLinearDao extends ValidatorDao{
  
  def assetWhichDoesNotFallInFullyToLink(assetType: Int, links: Set[String]): Seq[LinearReferenceAsset] = {
    val assetTypeFilter = s"a.asset_type_id = ${assetType}"
    val sql =
      s"""
             SELECT a.id, lp.link_id, lp.side_code, lp.start_measure, lp.end_measure
                FROM asset a
                JOIN asset_type aty ON a.asset_type_id = aty.id
                JOIN asset_link al ON a.id = al.asset_id
                JOIN lrm_position lp ON al.position_id = lp.id
                JOIN kgv_roadlink kr ON lp.link_id = kr.linkid
                WHERE aty.geometry_type = 'linear' and ${assetTypeFilter}
                AND a.floating = FALSE
                AND (a.valid_to > current_timestamp OR a.valid_to IS NULL)
                AND (lp.start_measure < 0.0 OR lp.end_measure > kr.geometrylength)
                ${filterLink("lp.link_id", links)}
                """

    StaticQuery.queryNA[LinearReferenceAsset](sql)(getResult).iterator.toSeq
  }

  def overFlappingAssets(assetType: Int, links: Set[String]): Seq[LinearReferenceAsset] = {

    val assetTypeFilter = s"a.asset_type_id = ${assetType}"
    val sql =
      s"""SELECT a.id, lp.link_id, lp.side_code, lp.start_measure, lp.end_measure
      FROM asset a
      JOIN asset_link al ON a.id = al.asset_id
      JOIN lrm_position lp ON al.position_id = lp.id
      WHERE ${assetTypeFilter}
      AND (a.valid_to > current_timestamp OR a.valid_to IS NULL)
      AND a.floating = FALSE ${filterLink("lp.link_id", links)}
      AND EXISTS (SELECT 1
          FROM asset a2
          JOIN asset_link al2 ON a2.id = al2.asset_id
          JOIN lrm_position lp2 ON al2.position_id = lp2.id
          WHERE a2.asset_type_id = a.asset_type_id
          AND a2.id != a.id
          AND a2.floating = FALSE
          AND (a2.valid_to > current_timestamp OR a2.valid_to IS NULL)
          AND lp2.link_id = lp.link_id
          AND (lp.side_code = 1 AND (lp2.side_code = 1 OR lp2.side_code = 2 OR lp2.side_code = 3)
          OR ((lp.side_code = 2 OR lp.side_code = 3) AND lp2.side_code = 1 OR lp2.side_code = lp.side_code))
          AND (((lp2.start_measure >= lp.start_measure AND lp2.start_measure < lp.end_measure) OR (lp2.end_measure > lp.start_measure AND lp2.end_measure <= lp.end_measure))
              OR ((lp.start_measure >= lp2.start_measure AND lp.start_measure < lp2.end_measure) OR (lp.end_measure > lp2.start_measure AND lp.end_measure <= lp2.end_measure)))
      )"""

    StaticQuery.queryNA[LinearReferenceAsset](sql)(getResult).iterator.toSeq
  }

  def assetTooShort(assetType: Int, links: Set[String]): Seq[LinearReferenceAsset] = {
    val assetTypeFilter = s"a.asset_type_id = ${assetType}"
    val sql =
      s"""
      SELECT a.id, lp.link_id, lp.side_code, lp.start_measure, lp.end_measure
      FROM asset a
      JOIN asset_link al ON a.id = al.asset_id
      JOIN lrm_position lp ON al.position_id = lp.id
      JOIN asset_type aty ON a.asset_type_id = aty.id
      JOIN kgv_roadlink kr ON lp.link_id = kr.linkid
      WHERE ${assetTypeFilter} and aty.geometry_type = 'linear'
      AND kr.geometrylength >= 2.0
      AND (lp.end_measure - lp.start_measure) < 2.0
      ${filterLink("lp.link_id", links)}
      """

    StaticQuery.queryNA[LinearReferenceAsset](sql)(getResult).iterator.toSeq
  }

  def assetDoesNotFillLink(assetType: Int, links: Set[String]): Seq[LinearReferenceAsset] = {
    val assetTypeFilter = s"a.asset_type_id = ${assetType}"
    val sql =
      s"""WITH result_set AS (
      SELECT a.id, lp.side_code, lp.link_id, lp.start_measure, lp.end_measure,
      (SELECT SUM(lp2.end_measure - lp2.start_measure) AS assets_total_lenght
           FROM asset a2
           JOIN asset_link al2 ON a2.id = al2.asset_id
           JOIN lrm_position lp2 ON al2.position_id = lp2.id
           WHERE a2.asset_type_id = a.asset_type_id
           AND lp2.link_id = lp.link_id ${filterLink("lp2.link_id", links)}
           AND a2.floating = FALSE
           AND (a2.valid_to > current_timestamp OR a2.valid_to IS NULL)
           AND ((lp.side_code = 1 AND lp2.side_code = 1)
              OR ((lp.side_code = 2 OR lp.side_code = 3) AND lp2.side_code = 1 OR lp2.side_code = lp.side_code))
      ),
      kr.geometrylength,
      (SELECT COUNT(a3.id) AS asset_count_on_same_or_bothdirections_side_code
          FROM asset a3
          JOIN asset_link al3 ON a3.id = al3.asset_id
          JOIN lrm_position lp3 ON al3.position_id = lp3.id
          WHERE a3.asset_type_id = a.asset_type_id
          AND lp3.link_id = lp.link_id ${filterLink("lp3.link_id", links)}
          AND a3.floating = FALSE
          AND (a3.valid_to > current_timestamp OR a3.valid_to IS NULL)
          AND ((lp.side_code = 1 AND lp3.side_code = 1)
           OR ((lp.side_code = 2 OR lp.side_code = 3) AND lp3.side_code = 1 OR lp3.side_code = lp.side_code))
          )
      FROM asset a
      JOIN asset_link al ON a.id = al.asset_id
      JOIN lrm_position lp ON al.position_id = lp.id
      JOIN kgv_roadlink kr ON lp.link_id = kr.linkid
      WHERE ${assetTypeFilter}
      AND a.floating = false
      AND (a.valid_to > current_timestamp OR a.valid_to IS NULL)
      AND (lp.side_code = 2 OR lp.side_code = 3 OR(lp.side_code = 1 AND NOT EXISTS(SELECT 1
          FROM asset a4
          JOIN asset_link al4 ON a4.id = al4.asset_id
          JOIN lrm_position lp4 ON al4.position_id = lp4.id
          WHERE a4.asset_type_id = a.asset_type_id
          AND lp4.link_id = lp.link_id
          AND a4.id != a.id
          AND a4.floating = false
          AND (a4.valid_to > current_timestamp OR a4.valid_to IS NULL)
          AND lp4.side_code != lp.side_code ${filterLink("lp4.link_id", links)}
          )
          )
      )
      )
      SELECT DISTINCT id, link_id,side_code, start_measure, end_measure
      FROM result_set
      WHERE assets_total_lenght != geometrylength ${filterLink("link_id", links)}"""
    StaticQuery.queryNA[LinearReferenceAsset](sql)(getResult).iterator.toSeq
  }

} 

object LaneValidatorDao extends ValidatorDao{
  def laneWhichDoesNotFallInFullyToLink(links: Set[String]): Seq[LinearReferenceAsset] = {
    val sql = s"""SELECT l.id , l.lane_code , lp.link_id , lp.side_code , lp.start_measure , lp.end_measure
                  FROM lane l
                  JOIN lane_link ll ON l.id = ll.lane_id
                  JOIN lane_position lp ON ll.lane_position_id = lp.id
                  JOIN kgv_roadlink kr ON lp.link_id = kr.linkid
                  WHERE (l.valid_to > current_timestamp OR l.valid_to IS NULL)
                  AND (lp.start_measure < 0.0 OR lp.end_measure > kr.geometrylength)
                  ${filterLink("link_id", links)}
                  """
    StaticQuery.queryNA[LinearReferenceAsset](sql)(getResultLane).iterator.toSeq
  }
  def overFlappingLanes(links: Set[String]): Seq[LinearReferenceAsset] = {
    val sql =
      s"""SELECT l.id, l.lane_code, lp.link_id, lp.side_code, lp.start_measure, lp.end_measure
      FROM lane l
      JOIN lane_link ll ON l.id = ll.lane_id
      JOIN lane_position lp ON ll.lane_position_id = lp.id
      WHERE (l.valid_to > current_timestamp OR l.valid_to IS NULL)
      AND EXISTS (SELECT 1
          FROM lane l2
          JOIN lane_link ll2 ON l2.id = ll2.lane_id
          JOIN lane_position lp2 ON ll2.lane_position_id = lp2.id
          WHERE (l2.valid_to > current_timestamp OR l2.valid_to IS NULL)
          AND l2.id != l.id
          AND lp2.link_id = lp.link_id
          AND lp2.side_code = lp.side_code
          AND l2.lane_code = l.lane_code
          ${filterLink("lp2.link_id", links)}
          AND (((lp2.start_measure >= lp.start_measure AND lp2.start_measure < lp.end_measure) OR (lp2.end_measure > lp.start_measure AND lp2.end_measure <= lp.end_measure))        
               OR ((lp.start_measure >= lp2.start_measure AND lp.start_measure < lp2.end_measure) OR (lp.end_measure > lp2.start_measure AND lp.end_measure <= lp2.end_measure)))  
      ) ${filterLink("lp.link_id", links)}
      """
    StaticQuery.queryNA[LinearReferenceAsset](sql)(getResultLane).iterator.toSeq
  }
  
  def laneTooShort(links: Set[String]): Seq[LinearReferenceAsset] = {
    val sql =
      s"""
      SELECT l.id, l.lane_code, lp.link_id, lp.side_code, lp.start_measure, lp.end_measure
      FROM lane l
      JOIN lane_link ll ON l.id = ll.lane_id
      JOIN lane_position lp ON ll.lane_position_id = lp.id
      JOIN kgv_roadlink kr ON lp.link_id = kr.linkid
      WHERE (l.valid_to > current_timestamp OR l.valid_to IS NULL)
      AND kr.geometrylength >= 2.0
      AND (lp.end_measure - lp.start_measure) < 2.0 ${filterLink("lp.link_id", links)}"""
    StaticQuery.queryNA[LinearReferenceAsset](sql)(getResultLane).iterator.toSeq
  }
  
  def incoherentLane(links: Set[String]): Seq[LinearReferenceAsset] = {
    val sql =
      s"""
      SELECT l.id, l.lane_code, lp.link_id, lp.side_code, lp.start_measure, lp.end_measure
      FROM lane l
      JOIN lane_link ll ON l.id = ll.lane_id
      JOIN lane_position lp ON ll.lane_position_id = lp.id
      WHERE l.lane_code != 1
      AND (l.valid_to > current_timestamp OR l.valid_to IS NULL)
      AND EXISTS(SELECT 1
          FROM lane l2
          JOIN lane_link ll2 ON l2.id = ll2.lane_id
          JOIN lane_position lp2 ON ll2.lane_position_id = lp2.id
          WHERE lp2.link_id = lp.link_id
          AND (l2.valid_to > current_timestamp OR l2.valid_to IS NULL)
          AND lp2.side_code = lp.side_code
          AND MOD(l.lane_code, 2) = MOD(l2.lane_code, 2)
          AND l2.lane_code < l.lane_code
          AND (lp2.start_measure > lp.start_measure OR lp2.end_measure < lp.end_measure) ${filterLink("lp2.link_id", links)}
      ) ${filterLink("lp.link_id", links)}"""
    StaticQuery.queryNA[LinearReferenceAsset](sql)(getResultLane).iterator.toSeq
  }
  
}

object PointAssetValidatorDao extends ValidatorDao{

  def assetWhichDoesNotFallInToLink(assetType: Int, links: Set[String]): Seq[LinearReferenceAsset] = {
    val assetTypeFilter = s"a.asset_type_id = ${assetType}"
    val sql = s"""
                SELECT a.id, lp.link_id, lp.side_code, lp.start_measure, lp.end_measure
                FROM asset a
                JOIN asset_type aty ON a.asset_type_id = aty.id
                JOIN asset_link al ON a.id = al.asset_id
                JOIN lrm_position lp ON al.position_id = lp.id
                JOIN kgv_roadlink kr ON lp.link_id = kr.linkid
                WHERE ${assetTypeFilter} and aty.geometry_type = 'point' 
                AND a.floating = FALSE
                AND (a.valid_to > current_timestamp OR a.valid_to IS NULL)
                AND (lp.start_measure < 0.0 OR lp.start_measure > kr.geometrylength) ${assetTypeFilter} 
                ${filterLink("lp.link_id", links)}
                """.stripMargin
    StaticQuery.queryNA[LinearReferenceAsset](sql)(getResult).iterator.toSeq
  }
  
}