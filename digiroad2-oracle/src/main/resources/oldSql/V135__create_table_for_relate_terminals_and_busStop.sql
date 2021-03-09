CREATE TABLE TERMINAL_BUS_STOP_LINK (
  TERMINAL_ASSET_ID REFERENCES ASSET(ID),
	BUS_STOP_ASSET_ID REFERENCES ASSET(ID),
  UNIQUE (TERMINAL_ASSET_ID, BUS_STOP_ASSET_ID)
);

CREATE INDEX TERMINAL_ASSET_ID_idx ON TERMINAL_BUS_STOP_LINK(TERMINAL_ASSET_ID);
CREATE INDEX BUS_STOP_ASSET_ID_idx ON TERMINAL_BUS_STOP_LINK(BUS_STOP_ASSET_ID);

--Add property Liitetty Terminaaliin to Bussipysäkit (massTransitStop)
INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Liitetty Terminaaliin','db_migration_v135', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 10, 'read_only_text', 0, 'db_migration_v135', 'liitetty terminaaliin', (select id from LOCALIZED_STRING where VALUE_FI = 'Liitetty Terminaaliin'));

INSERT INTO enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (primary_key_seq.nextval, 6, 'Terminaalipysäkit', ' ', 'db_migration_v135', (select id from property where public_id = 'pysakin_tyyppi'));