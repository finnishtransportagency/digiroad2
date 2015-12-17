package fi.liikennevirasto.digiroad2.util
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

object TrafficSignTestData {
  def createTestData() = {
    OracleDatabase.withDynTransaction {
      sqlu"""
        insert all
          into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600053,240,'dr2_test_data',235)
          into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600054,240,'dr2_test_data',235)
          into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600055,240,'dr2_test_data',235)
          select * from dual
      """.execute

      sqlu"""
            insert all
              into LRM_POSITION (ID,MML_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values (70000015, 388553074, 103, 103, 2)
              into LRM_POSITION (ID,START_MEASURE,END_MEASURE,MML_ID,SIDE_CODE) values (70000016,69,69,388553080, 3)
              into LRM_POSITION (ID,START_MEASURE,END_MEASURE,MML_ID,SIDE_CODE) values (70000017,100,100,12345, 3)
            select * from dual

      """.execute
      sqlu"""
            insert all
            into asset_link (ASSET_ID, POSITION_ID) values (600053, 70000015)
            into asset_link (ASSET_ID, POSITION_ID) values (600054, 70000016)
            into asset_link (ASSET_ID, POSITION_ID) values (600055, 70000017)
            select * from dual
      """.execute

      val twoLineText = "KESKUSTA:CENTRUM;;;;2;1;\nLAHTI:LAHTIS;;4;E75;2;1;"
      sqlu"""
            insert all
              into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (600056, 600053, (select id from property where public_id='opastustaulun_teksti'), 'HELSINKI:HELSINGFORS;;;;1;1;', sysdate, 'dr2_test_data')
              into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (600057, 600054, (select id from property where public_id='opastustaulun_teksti'), $twoLineText, sysdate, 'dr2_test_data')
              into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (600058, 600055, (select id from property where public_id='opastustaulun_teksti'), 'KESKUSTA:CENTRUM;4:7;E75;1;1;', sysdate, 'dr2_test_data')
            select * from dual
      """.execute

      sqlu"""
        UPDATE asset SET geometry = MDSYS.SDO_GEOMETRY(4401,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),MDSYS.SDO_ORDINATE_ARRAY(374467, 6677347, 0, 0)) WHERE id = 600053

      """.execute
      sqlu"""
            UPDATE asset
              SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                                3067,
                                                NULL,
                                                MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                                MDSYS.SDO_ORDINATE_ARRAY(374443.764141219, 6677245.28337185, 0, 0)
                                               )
              WHERE id = 600054
          """.execute
      sqlu"""
            UPDATE asset
              SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                                3067,
                                                NULL,
                                                MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                                MDSYS.SDO_ORDINATE_ARRAY(374396, 6677319 , 0, 0)
                                               )
              WHERE id = 600055
          """.execute
    }
  }
}
