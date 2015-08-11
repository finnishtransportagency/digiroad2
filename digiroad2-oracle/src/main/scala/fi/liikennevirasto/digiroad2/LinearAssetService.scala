package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.ConversionDatabase._

import slick.driver.JdbcDriver.backend.Database
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database.dynamicSession

object LinearAssetService {
  def getByMunicipality(typeId: Int, municipalityNumber: Int): Seq[Map[String, Any]] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
         select s.segm_id, s.tielinkki_id, s.puoli, s.arvo, to_2d(sdo_lrs.dynamic_segment(t.shape, s.alkum, s.loppum))
           from segments s
           join tielinkki_ctas t on s.tielinkki_id = t.dr1_id
           where t.kunta_nro = $municipalityNumber and s.tyyppi = $typeId
        """
      query.as[(Long, Long, Int, Int, Seq[Point])].iterator().map {
        case (id, roadLinkId, sideCode, value, geometry) =>
          Map(
            "id" -> (id.toString + "-" + roadLinkId.toString),
            "sideCode" -> sideCode,
            "value" -> value,
            "points" -> geometry)
      }.toSeq
    }
  }

  def getRoadAddressesByMunicipality(municipalityNumber: Int): Seq[Map[String, Any]] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
         select s.segm_id, s.tielinkki_id, s.puoli, to_2d(sdo_lrs.dynamic_segment(t.shape, s.alkum, s.loppum)), s.tienumero, s.tieosanumero, s.ajoratanumero
           from segm_tieosoite s
           join tielinkki_ctas t on s.tielinkki_id = t.dr1_id
           where t.kunta_nro = $municipalityNumber
        """
      query.as[(Long, Long, Int, Seq[Point], Int, Int, Int)].iterator().map {
        case (id, roadLinkId, sideCode, geometry, roadNumber, roadPartNumber, roadLaneNumber) =>
          Map(
            "id" -> (id.toString + "-" + roadLinkId.toString),
            "sideCode" -> sideCode,
            "points" -> geometry,
            "roadNumber" -> roadNumber,
            "roadPartNumber" -> roadPartNumber,
            "roadLaneNumber" -> roadLaneNumber)
      }.toSeq
    }
  }

  def getBridgesUnderpassesAndTunnelsByMunicipality(municipalityNumber: Int): Seq[Map[String, Any]] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
         select s.objectid, s.tielinkki_id, s.puoli, to_2d(sdo_lrs.dynamic_segment(t.shape, s.alkum, s.loppum)), s.tyyppi, s.name_fi, s.name_sv
           from silta_alik_tunn s
           join tielinkki_ctas t on s.tielinkki_id = t.dr1_id
           where t.kunta_nro = $municipalityNumber
        """
      query.as[(Long, Long, Int, Seq[Point], Int, String, String)].iterator().map {
        case (id, roadLinkId, sideCode, geometry, typeId, nameFi, nameSv) =>
          Map(
            "id" -> (id.toString + "-" + roadLinkId.toString),
            "sideCode" -> sideCode,
            "points" -> geometry,
            "type" -> typeId,
            "nameFi" -> nameFi,
            "nameSv" -> nameSv)
      }.toSeq
    }
  }
}
