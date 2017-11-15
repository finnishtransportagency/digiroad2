--Add property Vaihtoehtoinen link_id (massTransitStop)
INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Vaihtoehtoinen link_id','db_migration_v139', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 10, 'text', 0, 'db_migration_v139', 'alternative_link_id', (select id from LOCALIZED_STRING where VALUE_FI = 'Vaihtoehtoinen link_id'));
