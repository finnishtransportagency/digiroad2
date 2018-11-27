    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'2-akselisen telin rajoitus','db_migration_v187', sysdate);

    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'3-akselisen telin rajoitus','db_migration_v187', sysdate);

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Ajoneuvon suurin sallittu telimassa'), 'number', 0, 'db_migration_v187', 'bogie_weight_2_axel', (select id from LOCALIZED_STRING where VALUE_FI = '2-akselisen telin rajoitus'));

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Ajoneuvon suurin sallittu telimassa'), 'number', 0, 'db_migration_v187', 'bogie_weight_3_axel', (select id from LOCALIZED_STRING where VALUE_FI = '3-akselisen telin rajoitus'));



