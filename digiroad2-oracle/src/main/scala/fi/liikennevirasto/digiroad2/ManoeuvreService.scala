package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle

import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.{StaticQuery => Q}

object ManoeuvreService {
  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Long] = {
    Database.forDataSource(ConversionDatabase.dataSource).withDynTransaction {
      val municipalityFilter = if (municipalities.nonEmpty) "kunta_nro in (" + municipalities.mkString(",") + ") and" else ""
      val leftBottomX = bounds.leftBottom.x
      val leftBottomY = bounds.leftBottom.y
      val rightTopX = bounds.rightTop.x
      val rightTopY = bounds.rightTop.y
      sql"""
        select k.kaan_id
        from kaantymismaarays k
        join tielinkki_ctas tl on k.tl_dr1_id = tl.dr1_id
        where #$municipalityFilter
              mdsys.sdo_filter(shape,
                               sdo_cs.viewport_transform(
                               mdsys.sdo_geometry(
                               2003,
                               0,
                               NULL,
                               mdsys.sdo_elem_info_array(1,1003,3),
                               mdsys.sdo_ordinate_array($leftBottomX,
                                                        $leftBottomY,
                                                        $rightTopX,
                                                        $rightTopY)
                               ),
                               3067
                               ),
                               'querytype=WINDOW'
                               ) = 'TRUE'
      """.as[Long].list
    }
  }
}
