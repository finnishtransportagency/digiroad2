package fi.liikennevirasto.viite.dao

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.{StaticQuery => Q}

/**
  * Created by venholat on 27.10.2016.
  */
object MunicipalityDAO {
  def getMunicipalityMapping = {
    Q.queryNA[(Long, Long)]("""SELECT id, ely_nro FROM MUNICIPALITY""").list.map(x => x._1 -> x._2).toMap
  }

  def getMunicipalityRoadMaintainers = {
    Q.queryNA[(Long, Long)]("""SELECT id, ROAD_MAINTAINER_ID FROM MUNICIPALITY""").list.map(x => x._1 -> x._2).toMap
  }
}
