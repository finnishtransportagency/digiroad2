package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest.FunSuite
import org.scalatest.Matchers.{be, convertToAnyShouldWrapper}
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database.dynamicSession

class RoadLinkDAOSpec extends FunSuite {
  
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)
  test("Fetch link where road names has hyphen symbol in middle of word") {
    runWithRollback  {
      val dao = new RoadLinkDAO
      sqlu"""INSERT INTO roadlink (linkid, municipalitycode, roadname_fi, roadname_se, roadname_sm,constructiontype,shape) VALUES('1', 853, 'Tarkk''ampujankatu', 'Skarpskyttegatan', NULL,0,'SRID=3067;LINESTRING ZM(385935.666 6671107.833 19.858 0, 386028.217 6671112.363 20.596 92.661)'::geometry)""".execute
      sqlu"""INSERT INTO roadlink (linkid, municipalitycode, roadname_fi, roadname_se, roadname_sm,constructiontype,shape) VALUES('2', 441, 'Sammalinen', NULL, NULL,0,'SRID=3067;LINESTRING ZM(385935.666 6671107.833 19.85 0, 386028.217 6671112.363 20.596 92.661)'::geometry)""".execute
      dao.fetchByRoadNames("roadname_fi", Set("Tarkk'ampujankatu")).size should be (1)
      dao.fetchByRoadNames("roadname_fi", Set("Tarkk'ampujankatu","Sammalinen")).size should be (2)
    }
  }

}
