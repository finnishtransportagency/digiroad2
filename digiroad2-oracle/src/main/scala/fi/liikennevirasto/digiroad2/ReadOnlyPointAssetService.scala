package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.ConversionDatabase._

import slick.driver.JdbcDriver.backend.Database
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database.dynamicSession

object ReadOnlyPointAssetService {
  def getServicePoints(): Seq[Map[String, Any]] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
         select p.palv_tyyppi, p.palv_lisatieto, p.palv_rautatieaseman_tyyppi, p.palv_paikkojen_lukumaara, p.palv_lepoalue_tyyppi, to_2d(p.shape), p.dr1_oid, p.nimi_fi
           from palvelupisteet p
        """
      query.as[(Int, Option[String], Option[Int], Option[Int], Option[Int], Seq[Point], Long, Option[String])].iterator.map {
        case (serviceType, extraInfo, railwayStationType, parkingPlaceCount, restAreaType, geometry, id, name) =>
          Map("id" -> id, "point" -> geometry.head, "serviceType" -> serviceType, "extraInfo" -> extraInfo, "railwayStationType" -> railwayStationType, "parkingPlaceCount" -> parkingPlaceCount, "restAreaType" -> restAreaType, "name" -> name)
      }.toSeq
    }
  }

  def getByMunicipality(typeId: Int, municipalityNumber: Int): Seq[Map[String, Any]] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
         select s.segm_id, s.tielinkki_id, to_2d(sdo_lrs.dynamic_segment(t.shape, s.alkum, s.loppum)), s.puoli
           from segments s
           join tielinkki_ctas t on s.tielinkki_id = t.dr1_id
           where t.kunta_nro = $municipalityNumber and s.tyyppi = $typeId
        """
      query.as[(Long, Long, Seq[Point], Int)].iterator.map {
        case (id, roadLinkId, geometry, sideCode) => Map("id" -> id, "point" -> geometry.head, "sideCode" -> sideCode)
      }.toSeq
    }
  }
}
