--Create New Point Assets Types
--TR weight limit
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
    VALUES (320, 'Tierekisteri Suurin sallittu massa', 'point', 'db_migration_v152');

--TR trailer truck weight limit
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
    VALUES (330, 'Tierekisteri Yhdistelmän suurin sallittu massa', 'point', 'db_migration_v152');

--TR axle weight limit
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
    VALUES (340, 'Tierekisteri Suurin sallittu akselimassa', 'point', 'db_migration_v152');

--TR bogie weight limit
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
    VALUES (350, 'Tierekisteri Suurin sallittu telimassa', 'point', 'db_migration_v152');

--TR height limit
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
    VALUES (360, 'Tierekisteri Suurin sallittu korkeus', 'point', 'db_migration_v152');

--TR width limit
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
    VALUES (370, 'Tierekisteri Suurin sallittu leveys', 'point', 'db_migration_v152');




--Create New LOCALIZED_STRING for New Assets
INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval, 'Rajoitus', 'db_migration_v152', sysdate);

INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval, 'Syy', 'db_migration_v152', sysdate);




--Create New Properties for New Assets
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 320, 'number', 0, 'db_migration_v152', 'suurin_sallittu_massa_mittarajoitus', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Rajoitus'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 330, 'number', 0, 'db_migration_v152', 'yhdistelman_suurin_sallittu_massa', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Rajoitus'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 340, 'number', 0, 'db_migration_v152', 'suurin_sallittu_akselimassa', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Rajoitus'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 350, 'number', 0, 'db_migration_v152', 'suurin_sallittu_telimassa', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Rajoitus'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 360, 'number', 0, 'db_migration_v152', 'suurin_sallittu_korkeus', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Rajoitus'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 370, 'number', 0, 'db_migration_v152', 'suurin_sallittu_leveys', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Rajoitus'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 370, 'single_choice', 0, 'db_migration_v152', 'suurin_sallittu_leveys_syy', (select id from LOCALIZED_STRING where VALUE_FI = 'Syy'));



--Create default Values for the field Reason(Syy)
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 1, 'Silta', ' ', 'db_migration_v152', (select id from property where public_ID = 'suurin_sallittu_leveys_syy'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 2, 'Kokoportaali, muodostaa esteen koko ajoradalle', ' ', 'db_migration_v152', (select id from property where public_ID = 'suurin_sallittu_leveys_syy'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 3, 'Puoliportaali, esteenä vain toiseen suuntaan tai ajoradalle', ' ', 'db_migration_v152', (select id from property where public_ID = 'suurin_sallittu_leveys_syy'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 4, 'Kaide', ' ', 'db_migration_v152', (select id from property where public_ID = 'suurin_sallittu_leveys_syy'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 5, 'Valaisin', ' ', 'db_migration_v152', (select id from property where public_ID = 'suurin_sallittu_leveys_syy'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 6, 'Aita', ' ', 'db_migration_v152', (select id from property where public_ID = 'suurin_sallittu_leveys_syy'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 7, 'Maatuki', ' ', 'db_migration_v152', (select id from property where public_ID = 'suurin_sallittu_leveys_syy'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 8, 'Liikennevalopylväs', ' ', 'db_migration_v152', (select id from property where public_ID = 'suurin_sallittu_leveys_syy'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 9, 'Muu Alikulkueste', ' ', 'db_migration_v152', (select id from property where public_ID = 'suurin_sallittu_leveys_syy'));