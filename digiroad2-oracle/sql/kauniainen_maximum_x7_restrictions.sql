-- Assets Created for Unit Test of Height Limit Service (Tierekisterin suurin sallittu korkeus)
insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600074,360,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 1611387, 388553548, 16.592, 16.592, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600074, currval('lrm_position_primary_key_seq'));
UPDATE asset SET geometry =  ST_GeomFromText('POINT(374101.60105163435 6677437.872017591 0 0)',3067)
  WHERE id = 600074;

insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600075,360,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 1611387, 388553548, 16.592, 16.592, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600075, currval('lrm_position_primary_key_seq'));
UPDATE asset SET geometry = ST_GeomFromText('POINT(374101.60105163435 6677437.872017591 0 0)',3067)
  WHERE id = 600075;

-- Assets Created for Unit Test of Weight Limit Service (Tierekisterin suurin sallittu massa)
insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600076,320,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 1611387, 388553548, 16.592, 16.592, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600076, currval('lrm_position_primary_key_seq'));
UPDATE asset SET geometry =ST_GeomFromText('POINT(374101.60105163435 6677437.872017591 0 0)',3067)
  WHERE id = 600076;

-- Assets Created for Unit Test of AxleWeightLimit Service (Tierekisterin suurin sallittu akselimassa)
insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600077,340,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 1611387, 388553548, 16.592, 16.592, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600077, currval('lrm_position_primary_key_seq'));
UPDATE asset SET geometry = ST_GeomFromText('POINT(374101.60105163435 6677437.872017591 0 0)',3067)
    WHERE id = 600077;

-- Assets Created for Unit Test of BogieWeightLimit Service (Tierekisterin suurin sallittu telimassa)
insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600078,350,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 1611387, 388553548, 16.592, 16.592, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600078, currval('lrm_position_primary_key_seq'));
UPDATE asset SET geometry = ST_GeomFromText('POINT(374101.60105163435 6677437.872017591 0 0)',3067)
  WHERE id = 600078;

-- Assets Created for Unit Test of TrailerTruckWeightLimit Service (Tierekisterin yhdistelmän suurin sallittu massa)
insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600079,330,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 1611387, 388553548, 16.592, 16.592, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600079, currval('lrm_position_primary_key_seq'));
UPDATE asset SET geometry = ST_GeomFromText('POINT(374101.60105163435 6677437.872017591 0 0)',3067)
  WHERE id = 600079;

-- Assets Created for Unit Test of WidthLimit Service (Tierekisterin suurin sallittu leveys)
insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600080,370,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 1611387, 388553548, 16.592, 16.592, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600080, currval('lrm_position_primary_key_seq'));
UPDATE asset SET geometry = ST_GeomFromText('POINT(374101.60105163435 6677437.872017591 0 0)',3067)
  WHERE id = 600080;

  INSERT INTO SINGLE_CHOICE_VALUE (ASSET_ID, ENUMERATED_VALUE_ID, PROPERTY_ID)
        VALUES (
                  600080,
                  (SELECT id
                     FROM enumerated_value
                    WHERE     VALUE = 3
                          AND property_id =
                                 (SELECT id
                                    FROM property
                                   WHERE public_id =
                                            'suurin_sallittu_leveys_syy')),
                  (SELECT id
                     FROM property
                    WHERE public_id = 'suurin_sallittu_leveys_syy'));