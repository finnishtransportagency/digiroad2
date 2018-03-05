package fi.liikennevirasto.digiroad2.dao.linearasset

import fi.liikennevirasto.digiroad2.dao.{MultiValueLinearAssetDao, Sequences}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest.{FunSuite, Matchers}
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.{MultiTypeProperty, MultiTypePropertyValue}
import fi.liikennevirasto.digiroad2.linearasset.{MultiAssetValue, MultiValue}

class MultiValueLinearAssetDaoSpec extends FunSuite with Matchers {

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("fetch asset containing several properties") {
    val dao = new MultiValueLinearAssetDao
    val linkId = 1l

    runWithRollback {
      val assetTypeId = 999
      val assetId = Sequences.nextPrimaryKeySeqValue
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
      val (propId1, propId2, propId3, propId4, propId5, propId6, propId7) = (Sequences.nextPrimaryKeySeqValue,
        Sequences.nextPrimaryKeySeqValue,
        Sequences.nextPrimaryKeySeqValue,
        Sequences.nextPrimaryKeySeqValue,
        Sequences.nextPrimaryKeySeqValue,
        Sequences.nextPrimaryKeySeqValue,
        Sequences.nextPrimaryKeySeqValue)

      val numberValue1 = 666
      val numberValue2 = 777
      val enumeratedValue1 = "Avattava puomi"
      val enumeratedValue2 = "Ohituskielto"
      val textValue = "hope this works!"
      val testUser = "dr2_test_data"

      //create new asset type and asset for test purposes
      sqlu"""INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY) VALUES ($assetTypeId, 'Test asset type', 'linear', $testUser)""".execute
      sqlu"""insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY) VALUES ($assetId, $assetTypeId, $testUser)""".execute
      sqlu"""insert into LRM_POSITION (ID,LINK_ID,START_MEASURE,END_MEASURE,SIDE_CODE) VALUES ($lrmPositionId, $linkId, 0, 100, 1)""".execute
      sqlu"""insert into ASSET_LINK (ASSET_ID, POSITION_ID) VALUES ($assetId, $lrmPositionId)""".execute

      //Single choice value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, $assetTypeId, 'single_choice', 0, $testUser, 'test_single_choice', null)""".execute
      sqlu"""INSERT INTO single_choice_value(asset_id, enumerated_value_id, property_id)
           VALUES ($assetId, (select id from enumerated_value where name_fi=$enumeratedValue1), $propId1)""".execute

      //Multiple choice value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, $assetTypeId, 'multiple_choice', 0, $testUser, 'test_multiple_choice', null)""".execute
      sqlu"""INSERT INTO multiple_choice_value(id, property_id, asset_id, enumerated_value_id, modified_by)
           VALUES (1, $propId2, $assetId, (select id from enumerated_value where name_fi=$enumeratedValue2), $testUser)""".execute
      sqlu"""INSERT INTO multiple_choice_value(id, property_id, asset_id, enumerated_value_id, modified_by)
           VALUES(2, $propId2, $assetId, (select id from enumerated_value where name_fi=$enumeratedValue1), $testUser)""".execute

      //Number property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId3, $assetTypeId, 'read_only_number', 0, $testUser, 'test_data_number', null)""".execute
      sqlu"""INSERT INTO number_property_value(id, property_id, asset_id, value)
            VALUES ($propId4, $propId3, $assetId, $numberValue1)""".execute
      sqlu"""INSERT INTO number_property_value(id, property_id, asset_id, value)
            VALUES ($propId5, $propId3, $assetId, $numberValue2)""".execute

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId6, $assetTypeId, 'read_only_text', 0, $testUser, 'test_data_text', null)""".execute
      sqlu"""insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by)
            VALUES ($propId7, $assetId, $propId6, $textValue, sysdate, $testUser)""".execute

      val persistedAssets = dao.fetchMultiValueLinearAssetsByLinkIds(999, Seq(linkId))

      persistedAssets.size should be(1)
      persistedAssets.head.linkId should be(linkId)

      val assetValues = persistedAssets.head.value.get.asInstanceOf[MultiValue].value.properties
      assetValues.find(_.publicId == "test_data_text").get.values.head.value should be (textValue)
      assetValues.find(_.publicId == "test_multiple_choice").get.values should be (Seq(MultiTypePropertyValue(enumeratedValue2), MultiTypePropertyValue(enumeratedValue1)))
      assetValues.find(_.publicId == "test_single_choice").get.values.head.value should be (enumeratedValue1)
      assetValues.find(_.publicId == "test_data_number").get.values should be (Seq(MultiTypePropertyValue(numberValue1.toString()), MultiTypePropertyValue(numberValue2.toString())))
    }
  }
}
