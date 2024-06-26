package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.service.AssetOnExpiredLink
import org.joda.time.DateTime
import net.postgis.jdbc.PGgeometry
import net.postgis.jdbc.geometry.GeometryBuilder
import org.postgresql.util.PGobject
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.{GetResult, PositionedResult}

import scala.collection.mutable.ListBuffer

class AssetsOnExpiredLinksDAO {

  protected def extractGeometry(data: Object): List[List[Double]] = {
    val geometry = data.asInstanceOf[PGobject]
    if (geometry == null) Nil
    else {
      val geomValue = geometry.getValue
      val geom = GeometryBuilder.geomFromString(geomValue)
      val listOfPoint= ListBuffer[List[Double]]()
      for (i <- 0 until geom.numPoints() ){
        val point =geom.getPoint(i)
        listOfPoint += List(point.x, point.y, point.z, point.m)
      }
      listOfPoint.toList
    }
  }

  implicit val getRoadLink: GetResult[AssetOnExpiredLink] = new GetResult[AssetOnExpiredLink] {
    def apply(r: PositionedResult): AssetOnExpiredLink = {
      val id = r.nextInt()
      val assetTypeId = r.nextInt()
      val linkId = r.nextString()
      val sideCode = r.nextInt()
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()
      val roadLinkExpired = r.nextTimestamp()
      val pgAssetGeometry = r.nextObjectOption().map(extractGeometry).get

      val roadLinkExpiredDate = new DateTime(roadLinkExpired)
      val assetGeometry = pgAssetGeometry.map(point => Point(point(0), point(1), point(2)))

      AssetOnExpiredLink(id, assetTypeId, linkId, sideCode, startMeasure, endMeasure, assetGeometry, roadLinkExpiredDate)
    }
  }

  def fetchWorkListAssets(): Seq[AssetOnExpiredLink] = {
    sql"""SELECT asset_id, asset_type_id, link_id, side_code, start_measure, end_measure, road_link_expired_date, asset_geometry
          FROM assets_on_expired_road_links
       """.as[AssetOnExpiredLink].list
  }

  def insertToWorkList(asset: AssetOnExpiredLink): Unit = {
    val assetId = asset.id
    val assetTypeId = asset.assetTypeId
    val linkId = asset.linkId
    val sideCode = asset.sideCode
    val startMeasure = asset.startMeasure
    val endMeasure = asset.endMeasure
    val roadLinkExpiredDate = asset.roadLinkExpiredDate.toString
    val geometryWKT = if(asset.geometry.length > 1) {
      GeometryUtils.toWktLineString(asset.geometry).string
    } else if(asset.geometry.length == 1) GeometryUtils.toWktPoint(asset.geometry.head.x, asset.geometry.head.y).string
    else ""

    sqlu"""INSERT INTO assets_on_expired_road_links (asset_id, asset_type_id, link_id, side_code, start_measure, end_measure, road_link_expired_date, asset_geometry)
          VALUES ($assetId,
                  $assetTypeId,
                  $linkId,
                  $sideCode,
                  $startMeasure,
                  $endMeasure,
                  to_timestamp($roadLinkExpiredDate, 'YYYY-MM-DD"T"HH24:MI:SS.FF3'),
                  ST_GeomFromText($geometryWKT))
        """.execute
  }

  def deleteFromWorkList(assetIdsToDelete: Set[Long]): Unit = {
    val assetIdFilter = s"(${assetIdsToDelete.mkString(",")})"

    sqlu"""DELETE FROM assets_on_expired_road_links
           WHERE asset_id IN #$assetIdFilter
        """.execute
  }
}
