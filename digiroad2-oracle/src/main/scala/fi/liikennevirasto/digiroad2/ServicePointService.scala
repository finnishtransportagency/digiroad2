package fi.liikennevirasto.digiroad2

import _root_.oracle.jdbc.OracleData
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.pointasset.oracle.{OracleServicePointDao, ServicePoint}

class ServicePointService {
  def get(boundingBox: BoundingRectangle): Set[ServicePoint] = {
    OracleDatabase.withDynSession {
      OracleServicePointDao.get(boundingBox)
    }
  }

  val typeId: Int = 250
}


