package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.ConversionDatabase._
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.{Database, DatabaseDef}
import Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

object ServicePointImporter {
  def importServicePoints(database: DatabaseDef, vvhServiceHost: String): Unit =  {
    val servicePoints = database.withDynSession {
      sql"""
        select p.palv_tyyppi, p.palv_lisatieto, p.palv_rautatieaseman_tyyppi, p.palv_paikkojen_lukumaara, p.palv_lepoalue_tyyppi, to_2d(p.shape), p.dr1_oid, p.nimi_fi
        from palvelupisteet p
      """.as[(Int, Option[String], Option[Int], Option[Int], Option[Int], Seq[Point], Long, Option[String])].list
    }

    val groupSize = 3000
    val groupedServicePoints = servicePoints.grouped(groupSize).toList
    val totalGroupCount = groupedServicePoints.length

    OracleDatabase.withDynTransaction {
      val assetPS = dynamicSession.prepareStatement("insert into asset (id, asset_type_id, created_date, created_by) values (?, ?, SYSDATE, 'dr1_conversion')")
      val servicePointPS = dynamicSession.prepareStatement("insert into service_point_value (id, asset_id, type, name, additional_info, parking_place_count, type_extension) values (?,?,?,?,?,?,?)")

      println(s"*** Importing ${servicePoints.length} service points in $totalGroupCount groups of $groupSize each")

      groupedServicePoints.zipWithIndex.foreach { case (group, i) =>
        val assetIdToPoint = scala.collection.mutable.Map.empty[Long, Point]

        val startTime = DateTime.now()

        val servicesByPointId = group.groupBy(_._7)

        servicesByPointId.foreach { case (oid, rows) =>
          val assetId = Sequences.nextPrimaryKeySeqValue
          assetPS.setLong(1, assetId)
          assetPS.setInt(2, 250)
          assetIdToPoint += assetId -> rows.head._6.head
          assetPS.addBatch()

          rows.foreach { case (serviceType, additionalInfo, railwayStationType, parkingPlaceCount, restAreaType, _, _, name) =>
            servicePointPS.setLong(1, Sequences.nextPrimaryKeySeqValue)
            servicePointPS.setLong(2, assetId)
            servicePointPS.setInt(3, serviceType)
            servicePointPS.setString(4, name.orNull)
            servicePointPS.setString(5, additionalInfo.orNull)
            parkingPlaceCount.fold { servicePointPS.setNull(6, java.sql.Types.INTEGER) } { servicePointPS.setInt(6, _) }
            val typeExtension = (railwayStationType, restAreaType) match {
              case (Some(t), None) => Some(t + 4)
              case (None, Some(t)) => Some(t)
              case _ => None
            }
            typeExtension.fold { servicePointPS.setNull(7, java.sql.Types.INTEGER) } { servicePointPS.setInt(7, _) }
            servicePointPS.addBatch()
          }
        }

        assetPS.executeBatch()
        servicePointPS.executeBatch()

        assetIdToPoint.foreach { case (assetId, point) =>
          Queries.updateAssetGeometry(assetId, point)
        }

        println(s"*** Imported ${group.length} service points in ${AssetDataImporter.humanReadableDurationSince(startTime)} (done ${i + 1}/$totalGroupCount)" )
      }

      servicePointPS.close()
      assetPS.close()
    }
  }

}
