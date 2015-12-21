package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.pointasset.oracle.ServicePoint

class ServicePointService {
  def get(boundingBox: BoundingRectangle): Seq[ServicePoint] = {
    Nil
  }

  val typeId: Int = 250
}


