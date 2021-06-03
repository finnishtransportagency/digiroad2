package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

object ServicePointTestData {
  def createTestData() = {
    PostGISDatabase.withDynTransaction {
      sqlu"""
        insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600059,250,'dr2_test_data',235);
        insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600060,250,'dr2_test_data',235);
        insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600061,250,'dr2_test_data',235);
      """.execute

      sqlu"""
        insert into SERVICE_POINT_VALUE (ID,ASSET_ID,TYPE,ADDITIONAL_INFO,NAME,TYPE_EXTENSION, PARKING_PLACE_COUNT) values (600062,600059,11,'00127;;Tavaraliikenne;liikennepaikka','Rautatieasema',5,100);
        insert into SERVICE_POINT_VALUE (ID,ASSET_ID,TYPE,NAME,TYPE_EXTENSION) values (600063,600060,6,'Levähdyspaikka',3);
        insert into SERVICE_POINT_VALUE (ID,ASSET_ID,TYPE,ADDITIONAL_INFO,NAME,TYPE_EXTENSION, PARKING_PLACE_COUNT) values (600064,600061,8,'00023;Henkilöliikenne;;liikennepaikka','Lentokenttä',NULL,200);
        insert into SERVICE_POINT_VALUE (ID,ASSET_ID,TYPE,NAME,TYPE_EXTENSION) values (600065,600061,6,'Levähdyspaikka',2);
      """.execute

      sqlu"""
          UPDATE asset SET geometry = ST_GeomFromText('POINT(373911 6677357 0 0)',3067) WHERE id = 600059
      """.execute
      sqlu"""
          UPDATE asset SET geometry = ST_GeomFromText('POINT(374051 6677452 0 0)',3067) WHERE id = 600060
      """.execute
      sqlu"""
          UPDATE asset SET geometry = ST_GeomFromText('POINT(374128 6677512 0 0)',3067) WHERE id = 600061
      """.execute
    }
  }
}
