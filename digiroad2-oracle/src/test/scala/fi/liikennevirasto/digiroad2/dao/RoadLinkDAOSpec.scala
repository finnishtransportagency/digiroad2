package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.ConstructionType
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, TestTransactions}
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.scalatest.FunSuite
import org.scalatest.Matchers.{be, contain, convertToAnyShouldWrapper}
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database.dynamicSession

class RoadLinkDAOSpec extends FunSuite {

  val dao = new RoadLinkDAO
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("Fetch link where road names has hyphen symbol in middle of word") {
    runWithRollback  {
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
    val exposeDao = new ExposeDao
    val dateLong = new DateTime(now)
    
    exposeDao.extractModifiedDate(Some(1L),Some(dateLong.getMillis)).get.toString should be(dateLong.toString)
    exposeDao.extractModifiedDate(Some(1L),None).get.toString should be(new DateTime(1).toString())
  }

  test("only valid links are fetched") {
    val (linkId1, linkId2, linkId3) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
    runWithRollback {
      sqlu"""insert into kgv_roadlink (linkId, municipalitycode, constructiontype, shape) values ($linkId1, 235, ${ConstructionType.InUse.value},
            'SRID=3067;LINESTRING ZM(385935.666 6671107.833 19.858 0, 386028.217 6671112.363 20.596 92.661)'::geometry)""".execute
      sqlu"""insert into kgv_roadlink (linkId, municipalitycode, constructiontype, shape) values ($linkId2, 235, ${ConstructionType.InUse.value},
            'SRID=3067;LINESTRING ZM(385935.666 6671107.833 19.85 0, 386028.217 6671112.363 20.596 92.661)'::geometry)""".execute
      sqlu"""insert into kgv_roadlink (linkId, municipalitycode, constructiontype, shape, expired_date) values ($linkId3, 235, ${ConstructionType.InUse.value},
            'SRID=3067;LINESTRING ZM(385935.666 6671107.833 19.85 0, 386028.217 6671112.363 20.596 92.661)'::geometry, '2022-05-10 10:52:28.783')""".execute
      val validLinks = dao.fetchByLinkIds(Set(linkId1, linkId2, linkId3))
      validLinks.size should be(2)
      val validLinkIds = validLinks.map(_.linkId)
      validLinkIds should contain(linkId1)
      validLinkIds should contain(linkId2)
      validLinkIds shouldNot contain(linkId3)
    }
  }

  test("only expired links are fetched") {
    val (linkId1, linkId2, linkId3) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
    runWithRollback {
      sqlu"""insert into kgv_roadlink (linkId, municipalitycode, constructiontype, shape) values ($linkId1, 235, ${ConstructionType.InUse.value},
            'SRID=3067;LINESTRING ZM(385935.666 6671107.833 19.858 0, 386028.217 6671112.363 20.596 92.661)'::geometry)""".execute
      sqlu"""insert into kgv_roadlink (linkId, municipalitycode, constructiontype, shape) values ($linkId2, 235, ${ConstructionType.InUse.value},
            'SRID=3067;LINESTRING ZM(385935.666 6671107.833 19.85 0, 386028.217 6671112.363 20.596 92.661)'::geometry)""".execute
      sqlu"""insert into kgv_roadlink (linkId, municipalitycode, constructiontype, shape, expired_date) values ($linkId3, 235, ${ConstructionType.InUse.value},
            'SRID=3067;LINESTRING ZM(385935.666 6671107.833 19.85 0, 386028.217 6671112.363 20.596 92.661)'::geometry, '2022-05-10 10:52:28.783')""".execute
      val expiredLinks = dao.fetchExpiredRoadLinks()
      val expiredLinkIds = expiredLinks.map(_.roadLink.linkId)
      expiredLinkIds shouldNot contain (linkId1)
      expiredLinkIds shouldNot contain (linkId2)
      expiredLinkIds should contain (linkId3)
      }
    }
}
