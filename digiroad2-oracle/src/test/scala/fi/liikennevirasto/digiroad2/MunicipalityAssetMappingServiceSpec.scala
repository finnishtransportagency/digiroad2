package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.dao.{MunicipalityAssetMapping, Sequences}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.MunicipalityAssetMappingService
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class MunicipalityAssetMappingServiceSpec extends FunSuite with Matchers {

  val mappingService: MunicipalityAssetMappingService = new MunicipalityAssetMappingService

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)

  val testMapping1: MunicipalityAssetMapping = MunicipalityAssetMapping(1234567, "municipalityAssetId1", 235)
  val testMapping2: MunicipalityAssetMapping = MunicipalityAssetMapping(1234568, "municipalityAssetId2", 235)

  test("Update existing mapping with replacing asset id") {
    runWithRollback {
      val oldAssetId = Sequences.nextPrimaryKeySeqValue
      val replacingAssetId = Sequences.nextPrimaryKeySeqValue

      sqlu"""insert into asset (id, asset_type_id) values ($oldAssetId, 220)""".execute
      sqlu"""insert into asset (id, asset_type_id) values ($replacingAssetId, 220)""".execute
      sqlu"""insert into municipality_asset_id_mapping (asset_id, municipality_asset_id, municipality_code)
             values ($oldAssetId, ${testMapping1.municipalityAssetId}, ${testMapping1.municipality})
          """.execute

      val existing = mappingService.getByAssetId(oldAssetId, newTransaction = false)
      existing.isDefined should be(true)

      mappingService.updateIdMappings(oldAssetId, replacingAssetId, newTransaction = false)

      val afterUpdateOld = mappingService.getByAssetId(oldAssetId, newTransaction = false)
      val afterUpdateNew = mappingService.getByAssetId(replacingAssetId, newTransaction = false)

      afterUpdateOld.isDefined should be(false)
      afterUpdateNew.isDefined should be(true)
      afterUpdateNew.get.assetId should be(replacingAssetId)
      afterUpdateNew.get.municipalityAssetId should be(testMapping1.municipalityAssetId)
      afterUpdateNew.get.municipality should be(testMapping1.municipality)
    }
  }

  test("Updating mappings with asset without mapping do nothing") {
    runWithRollback {
      val oldAssetId = Sequences.nextPrimaryKeySeqValue
      val replacingAssetId = Sequences.nextPrimaryKeySeqValue

      sqlu"""insert into asset (id, asset_type_id) values ($oldAssetId, 220)""".execute
      sqlu"""insert into asset (id, asset_type_id) values ($replacingAssetId, 220)""".execute

      val existing = mappingService.getByAssetId(oldAssetId, newTransaction = false)
      existing.isDefined should be(false)

      mappingService.updateIdMappings(oldAssetId, replacingAssetId, newTransaction = false)

      val afterUpdateOld = mappingService.getByAssetId(oldAssetId, newTransaction = false)
      val afterUpdateNew = mappingService.getByAssetId(replacingAssetId, newTransaction = false)

      afterUpdateOld.isDefined should be(false)
      afterUpdateNew.isDefined should be(false)
    }
  }
}
