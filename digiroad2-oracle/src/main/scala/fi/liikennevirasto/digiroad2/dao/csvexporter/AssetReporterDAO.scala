package fi.liikennevirasto.digiroad2.dao.csvexporter

import fi.liikennevirasto.digiroad2.asset.{Manoeuvres, Lanes, ServicePoints, TrafficSigns}
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.{GetResult, PositionedResult}
import slick.jdbc.StaticQuery.interpolation


case class AssetReport(assetType: Int, assetNameFI: String, assetGeometryType: String, modifiedBy: String, modifiedDate: DateTime)

class AssetReporterDAO {

  implicit val getResult = new GetResult[AssetReport] {
    def apply(r: PositionedResult) : AssetReport = {
      val assetType = r.nextInt()
      val assetNameFI = r.nextString()
      val assetGeomType = r.nextString()
      val modifiedBy = r.nextString()
      val modifiedDate = new DateTime( r.nextTimestamp() )

      AssetReport(assetType, assetNameFI, assetGeomType, modifiedBy, modifiedDate)
    }
  }

  def linearAssetQuery(linkIds: Seq[Long], assetTypesList: Seq[Int]): List[AssetReport] = {
    val filter = if (assetTypesList.isEmpty) "" else s"AND a.asset_type_id IN (${assetTypesList.mkString(",")})"

    MassQuery.withIds(linkIds.toSet){idTableName =>
      sql"""SELECT a.ASSET_TYPE_ID, at2.NAME, at2.GEOMETRY_TYPE, a.MODIFIED_BY, a.MODIFIED_DATE
      FROM ASSET a
        JOIN ASSET_TYPE at2 ON at2.id = a.asset_type_id
        JOIN ASSET_LINK al ON a.id = al.asset_id
        JOIN LRM_POSITION pos ON al.position_id = pos.id
        JOIN #$idTableName i ON i.id = pos.link_id
      WHERE (a.valid_to IS NULL OR a.valid_to > SYSDATE)
      AND at2.GEOMETRY_TYPE = 'linear'
      #$filter
      """.as[AssetReport](getResult).list
    }
  }

  def laneQuery(linkIds: Seq[Long]): List[AssetReport] = {
    MassQuery.withIds(linkIds.toSet){ idTableName =>
      sql"""SELECT ${Lanes.typeId}, ${Lanes.nameFI}, ${Lanes.geometryType}, l.MODIFIED_BY, l.MODIFIED_DATE
      FROM LANE l
        JOIN LANE_LINK ll ON l.id = ll.lane_id
        JOIN LANE_POSITION pos ON ll.lane_position_id = pos.id
        JOIN #$idTableName i ON i.id = pos.link_id
      """.as[AssetReport](getResult).list
    }
  }

  def manoeuvreQuery(linkIds: Seq[Long]): List[AssetReport] = {
    MassQuery.withIds(linkIds.toSet){ idTableName =>
      sql"""SELECT ${Manoeuvres.typeId}, ${Manoeuvres.nameFI}, ${Manoeuvres.geometryType}, m.MODIFIED_BY, m.MODIFIED_DATE
      FROM MANOEUVRE m
      WHERE (m.valid_to IS NULL OR m.valid_to > SYSDATE)
      AND EXISTS
        (SELECT * FROM MANOEUVRE_ELEMENT me
          JOIN #$idTableName i ON i.id = me.link_id OR i.id = me.dest_link_id
        WHERE me.manoeuvre_id = m.id)
      """.as[AssetReport](getResult).list
    }
  }

  def pointAssetQuery(linkIds: Seq[Long], assetTypesList: Seq[Int]): List[AssetReport] = {
    MassQuery.withIds(linkIds.toSet){ idTableName =>
      sql"""SELECT a.ASSET_TYPE_ID, at2.NAME, at2.GEOMETRY_TYPE, a.MODIFIED_BY, a.MODIFIED_DATE
      FROM ASSET a
        JOIN ASSET_TYPE at2 ON at2.id = a.asset_type_id
        JOIN ASSET_LINK al ON a.id = al.asset_id
        JOIN LRM_POSITION pos ON al.position_id = pos.id
        JOIN #$idTableName i ON i.id = pos.link_id
      WHERE (a.valid_to IS NULL OR a.valid_to > SYSDATE)
      AND at2.GEOMETRY_TYPE = 'point'
      AND a.asset_type_id IN (#${assetTypesList.mkString(",")})
      """.as[AssetReport](getResult).list
    }
  }

  def servicePointQuery(municipalities: Seq[Long]): List[AssetReport] = {
    MassQuery.withIds(municipalities.toSet){ idTableName =>
      sql"""SELECT a.ASSET_TYPE_ID, at2.NAME, at2.GEOMETRY_TYPE, a.MODIFIED_BY, a.MODIFIED_DATE
      FROM ASSET a
        JOIN ASSET_TYPE at2 ON at2.id = a.asset_type_id
        JOIN #$idTableName i ON i.id = a.municipality_code
      WHERE (a.valid_to IS NULL OR a.valid_to > SYSDATE)
      AND a.asset_type_id = ${ServicePoints.typeId}
      """.as[AssetReport](getResult).list
    }
  }

  def getTotalTrafficSignNewLaw(municipalityCode: Int): Int = {
    sql"""SELECT COUNT(a.ID)
      FROM ASSET a
      WHERE (a.valid_to IS NULL OR a.valid_to > sysdate)
      AND a.created_date >= TO_DATE('01/06/2020 00:00:00', 'DD/MM/YYYY HH24:MI:SS')
      AND a.asset_type_id = #${TrafficSigns.typeId}
      AND a.municipality_code = #$municipalityCode""".as[Int].first

  }

}
