package fi.liikennevirasto.digiroad2.util
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase

object TrafficSignTestData {
  def createTestData() = {
    PostGISDatabase.withDynTransaction {
      sqlu"""
          insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600053,240,'dr2_test_data',235);
          insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600054,240,'dr2_test_data',235);
          insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600055,240,'dr2_test_data',235);
      """.execute
      sqlu"""
          insert into LRM_POSITION (ID,MML_ID,link_id,START_MEASURE,END_MEASURE,SIDE_CODE) values (70000015, 388553074, 1611317, 103, 103, 2);
          insert into LRM_POSITION (ID,START_MEASURE,END_MEASURE,MML_ID,link_id,SIDE_CODE) values (70000016,69,69,388553080,1611341, 3);
          insert into LRM_POSITION (ID,START_MEASURE,END_MEASURE,MML_ID,SIDE_CODE) values (70000017,100,100,12345, 3);
      """.execute
      sqlu"""
          insert into asset_link (ASSET_ID, POSITION_ID) values (600053, 70000015);
          insert into asset_link (ASSET_ID, POSITION_ID) values (600054, 70000016);
          insert into asset_link (ASSET_ID, POSITION_ID) values (600055, 70000017);
      """.execute

      val twoLineText = "KESKUSTA:CENTRUM;;;;2;1;\nLAHTI:LAHTIS;;4;E75;2;1;"
      sqlu"""
          insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (600056, 600053, (select id from property where public_id='opastustaulun_teksti'), 'HELSINKI:HELSINGFORS;;;;1;1;', current_timestamp, 'dr2_test_data');
          insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (600057, 600054, (select id from property where public_id='opastustaulun_teksti'), $twoLineText, current_timestamp, 'dr2_test_data');
          insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (600058, 600055, (select id from property where public_id='opastustaulun_teksti'), 'KESKUSTA:CENTRUM;4:7;E75;1;1;', current_timestamp, 'dr2_test_data');
      """.execute

      sqlu"""
        UPDATE asset SET geometry = ST_GeomFromText('POINT(374467 6677347 0 0)',3067) WHERE id = 600053
      """.execute
      sqlu"""
        UPDATE asset SET geometry = ST_GeomFromText('POINT(374443.764141219 6677245.28337185 0 0)',3067) WHERE id = 600054
      """.execute
      sqlu"""
        UPDATE asset SET geometry = ST_GeomFromText('POINT(374396 6677319 0 0)',3067) WHERE id = 600055
      """.execute
    }
  }
}