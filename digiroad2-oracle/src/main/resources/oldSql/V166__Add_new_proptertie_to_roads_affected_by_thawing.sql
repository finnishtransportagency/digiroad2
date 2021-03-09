--Add property Weight Limitation to Kelirikko asset type
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 130, 'number', 0, 'db_migration_v159', 'kelirikko', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Rajoitus'));
