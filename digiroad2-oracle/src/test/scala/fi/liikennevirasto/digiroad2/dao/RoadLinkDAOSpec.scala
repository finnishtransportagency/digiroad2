package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.ConstructionType
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, TestTransactions}
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.scalatest.FunSuite
import org.scalatest.Matchers.{be, convertToAnyShouldWrapper}
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database.dynamicSession

class RoadLinkDAOSpec extends FunSuite {
  
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)
  test("Fetch link where road names has hyphen symbol in middle of word") {
    runWithRollback  {
      val dao = new RoadLinkDAO
      val (linkId1, linkId2) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
      sqlu"""INSERT INTO kgv_roadlink (linkid, municipalitycode, roadname_fi, roadname_se,constructiontype,shape) VALUES($linkId1, 853, 'Tarkk''ampujankatu', 'Skarpskyttegatan',${ConstructionType.InUse.value},'SRID=3067;LINESTRING ZM(385935.666 6671107.833 19.858 0, 386028.217 6671112.363 20.596 92.661)'::geometry)""".execute
      sqlu"""INSERT INTO kgv_roadlink (linkid, municipalitycode, roadname_fi, roadname_se,constructiontype,shape) VALUES($linkId2, 441, 'Sammalinen', NULL,${ConstructionType.InUse.value},'SRID=3067;LINESTRING ZM(385935.666 6671107.833 19.85 0, 386028.217 6671112.363 20.596 92.661)'::geometry)""".execute
      dao.fetchByRoadNames("roadname_fi", Set("Tarkk'ampujankatu")).size should be (1)
      dao.fetchByRoadNames("roadname_fi", Set("Tarkk'ampujankatu","Sammalinen")).size should be (2)
    }
  }

  test("Select lastEdited or createdDate") {
    class ExposeDao extends RoadLinkDAO{
      override def extractModifiedDate(createdDate:Option[Long], lastEdited:Option[Long]): Option[DateTime]={
        super.extractModifiedDate(createdDate,lastEdited)
      }
    }
    val dao = new ExposeDao
    val dateLong = new DateTime(now)
    
    dao.extractModifiedDate(Some(1L),Some(dateLong.getMillis)).get.toString should be(dateLong.toString)
    dao.extractModifiedDate(Some(1L),None).get.toString should be(new DateTime(1).toString())
  }
  
}
