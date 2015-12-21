package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

object ServicePointTestData {
  def createTestData() = {
    OracleDatabase.withDynTransaction {
      sqlu"""
        insert all
           into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600059,250,'dr2_test_data',235)
           into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600060,250,'dr2_test_data',235)
           into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600061,250,'dr2_test_data',235)
        select * from dual
      """.execute

      sqlu"""
        insert all
           into SERVICE_POINT_VALUE (ID,ASSET_ID,TYPE,ADDITIONAL_INFO,NAME,TYPE_EXTENSION) values (600062,600059,11,'00127;;Tavaraliikenne;liikennepaikka','Rautatieasema',5)
           into SERVICE_POINT_VALUE (ID,ASSET_ID,TYPE,NAME,TYPE_EXTENSION) values (600063,600060,6,'Levähdyspaikka',3)
           into SERVICE_POINT_VALUE (ID,ASSET_ID,TYPE,ADDITIONAL_INFO,NAME,TYPE_EXTENSION) values (600064,600061,8,'00023;Henkilöliikenne;;liikennepaikka','Lentokenttä',NULL)
        select * from dual
      """.execute

      sqlu"""
         UPDATE asset
           SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                             3067,
                                             NULL,
                                             MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                             MDSYS.SDO_ORDINATE_ARRAY(373911,6677357, 0, 0)
                                            )
           WHERE id = 600059
      """.execute
      sqlu"""
            UPDATE asset
              SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                                3067,
                                                NULL,
                                                MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                                MDSYS.SDO_ORDINATE_ARRAY(374051, 6677452, 0, 0)
                                               )
              WHERE id = 600060
          """.execute
      sqlu"""
            UPDATE asset
              SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                                3067,
                                                NULL,
                                                MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                                MDSYS.SDO_ORDINATE_ARRAY(374128, 6677512 , 0, 0)
                                               )
              WHERE id = 600061
          """.execute
    }
  }
}
