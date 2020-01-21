
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval, 'Alkupäivämäärä', 'db_migration_v217', CURRENT_TIMESTAMP);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval, 'Loppupäivämäärä', 'db_migration_v217', CURRENT_TIMESTAMP);

-- Add Start and end Date to traffic Signs
INSERT INTO PROPERTY(ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_DATE, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
    VALUES (primary_key_seq.nextval, (SELECT ID FROM ASSET_TYPE WHERE NAME = 'Liikennemerkki'), 'date', '0', CURRENT_TIMESTAMP , 'db_migration_v217', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Alkupäivämäärä'), 'trafficSign_start_date');

INSERT INTO PROPERTY(ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_DATE, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
    VALUES (primary_key_seq.nextval, (SELECT ID FROM ASSET_TYPE WHERE NAME = 'Liikennemerkki'), 'date', '0', CURRENT_TIMESTAMP , 'db_migration_v217', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Loppupäivämäärä'), 'trafficSign_end_date');
