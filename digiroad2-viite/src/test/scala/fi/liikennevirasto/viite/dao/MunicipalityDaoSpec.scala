package fi.liikennevirasto.viite.dao

import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase


/**
  * Created by pedrosag on 31-10-2016.
  */
class MunicipalityDaoSpec extends FunSuite with Matchers{

  def runWithRollback(f: => Unit): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }

  test("Try to fetch a municipality and ely_nro values"){
    runWithRollback{
      val municipalityMap = MunicipalityDAO.getMunicipalityMapping
      municipalityMap.isEmpty should be(false)
      municipalityMap.keySet.forall(_ > 0) should be(true)
      municipalityMap.values.forall(_ >= 0) should be(true)
    }
  }

  test("Try to fetch a municipality and road maintainer values"){
    runWithRollback{
      val municipalityRoadMaitainerMap = MunicipalityDAO.getMunicipalityRoadMaintainers
      municipalityRoadMaitainerMap.isEmpty should be(false)
      municipalityRoadMaitainerMap.keySet.forall(_ > 0) should be(true)
      municipalityRoadMaitainerMap.values.forall(_ >= 0) should be(true)
    }
  }


  test("Verify if the road maintainer is correct for a ElyNro value") {
    runWithRollback{
      val municipalityMap = MunicipalityDAO.getMunicipalityMapping
      municipalityMap.isEmpty should be(false)
      val (selectedMunicipalityId, selectedElyNro) = municipalityMap.head
      val municipalityRoadMaintainerMap = MunicipalityDAO.getMunicipalityRoadMaintainers
      municipalityRoadMaintainerMap.isEmpty should be(false)
      municipalityRoadMaintainerMap.contains(selectedMunicipalityId) should be (true)
      selectedElyNro match {
        case 1 => municipalityRoadMaintainerMap.get(selectedMunicipalityId).get should be (14)
        case 2 => municipalityRoadMaintainerMap.get(selectedMunicipalityId).get should be (12)
        case 3 => municipalityRoadMaintainerMap.get(selectedMunicipalityId).get should be (10)
        case 4 => municipalityRoadMaintainerMap.get(selectedMunicipalityId).get should be (9)
        case 5 => municipalityRoadMaintainerMap.get(selectedMunicipalityId).get should be (8)
        case 6 => municipalityRoadMaintainerMap.get(selectedMunicipalityId).get should be (4)
        case 7 => municipalityRoadMaintainerMap.get(selectedMunicipalityId).get should be (2)
        case 8 => municipalityRoadMaintainerMap.get(selectedMunicipalityId).get should be (3)
        case 9 => municipalityRoadMaintainerMap.get(selectedMunicipalityId).get should be (1)
        case 0 => municipalityRoadMaintainerMap.get(selectedMunicipalityId).get should be (0)
      }
    }
  }
}
