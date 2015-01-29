package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.ConversionDatabase._

import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession

object VehiclePermittedService {
  def getByMunicipality(municipalityNumber: Int): Seq[Map[String, Any]] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
         select s.segm_id, s.tielinkki_id, s.puoli, s.arvo, to_2d(sdo_lrs.dynamic_segment(t.shape, s.alkum, s.loppum))
           from segments s
           join tielinkki_ctas t on s.tielinkki_id = t.dr1_id
           where t.kunta_nro = $municipalityNumber and s.tyyppi = 1
        """
      query.as[(Long, Long, Int, Int, Seq[Point])].iterator().map {
        case (id, roadLinkId, sideCode, value, geometry) =>
          Map(
            "id" -> (id.toString + "-" + roadLinkId.toString),
            "sideCode" -> sideCode,
            "value" -> value,
            "geometry" -> geometry)
      }.toSeq
    }
  }
}
