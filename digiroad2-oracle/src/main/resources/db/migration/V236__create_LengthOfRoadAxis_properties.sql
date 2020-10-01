-- NEW ASSET TYPE
INSERT INTO ASSET_TYPE(ID, NAME, GEOMETRY_TYPE, CREATED_BY)
VALUES (460, 'Pituussuuntaiset tiemerkinnät', 'linear', 'db_migration_v236');

-- LOCALIZED_STRING VALUE
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Asetusnumero', 'db_migration_v236', SYSDATE);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Kaistan numero', 'db_migration_v236', SYSDATE);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Suhteellinen sijainti', 'db_migration_v236', SYSDATE);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Merkinnän materiaali', 'db_migration_v236', SYSDATE);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Merkinnän pituus', 'db_migration_v236', SYSDATE);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Merkinnän leveys', 'db_migration_v236', SYSDATE);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Profiilimerkintä', 'db_migration_v236', SYSDATE);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Loppu päivämäärä', 'db_migration_v236', SYSDATE);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Alku päivämäärä', 'db_migration_v236', SYSDATE);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Jyrsitty', 'db_migration_v236', SYSDATE);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Kuntanumero', 'db_migration_v236', SYSDATE);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Herätemerkintä', 'db_migration_v236', SYSDATE);

-- add propeties
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'single_choice', 1, 'db_migration_v236', 'PT_regulatory_number',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Asetusnumero'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'number', 0, 'db_migration_v236', 'PT_lane_number',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Kaistan numero'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'PT_lane_type',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Kaistan tyyppi'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'number', 1, 'db_migration_v236', 'PT_lane_location',
        (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Suhteellinen sijainti'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'single_choice', 0, 'db_migration_v236', 'PT_material',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Merkinnän materiaali'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'number', 0, 'db_migration_v236', 'PT_length',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Merkinnän pituus'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'number', 0, 'db_migration_v236', 'PT_width',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Merkinnän leveys'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'single_choice', 0, 'db_migration_v236', 'PT_profile_mark',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Profiilimerkintä'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'PT_additional_information',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Lisätieto'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID,
                      DEFAULT_VALUE)
VALUES (primary_key_seq.nextval, 460, 'single_choice', 0, 'db_migration_v236', 'PT_state',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Tila'), 3);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'date', 0, 'db_migration_v236', 'PT_end_date',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Loppu päivämäärä'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'date', 0, 'db_migration_v236', 'PT_start_date',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Alku päivämäärä'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'single_choice', 0, 'db_migration_v236', 'PT_milled',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Jyrsitty'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'single_choice', 0, 'db_migration_v236', 'PT_condition',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Kunto'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'single_choice', 0, 'db_migration_v236', 'PT_rumble_strip',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Herätemerkintä'));

-- ENUMARATED VALUE CONDITION
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 99, 'Ei tietoa', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_condition'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Erittäin huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_condition'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_condition'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Tyydyttävä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_condition'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_condition'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Erittäin hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_condition'));
--material
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 99, 'Ei tietoa', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_material'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Maali', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_material'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Massa', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_material'));

-- RoadMark asetusnumero
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 99, 'Ei tietoa', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_regulatory_number'));
INSERT INTO ENUMERATED_VALUE (ID,VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval,1, 'K1 keskiviiva', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_regulatory_number'));
INSERT INTO ENUMERATED_VALUE (ID,VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2,'K2 ajokaistaviiva', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_regulatory_number'));
INSERT INTO ENUMERATED_VALUE (ID,VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3,'K3 Sulkuviiva', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_regulatory_number'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE,NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4,'K4 Varoitusviiva', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_regulatory_number'));
INSERT INTO ENUMERATED_VALUE (ID,VALUE ,NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5,'K5 Sulkualue', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_regulatory_number'));
INSERT INTO ENUMERATED_VALUE (ID,VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 6,'K6 Reunaviiva', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_regulatory_number'));

-- profile mark
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 99, 'Ei tietoa', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_profile_mark'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Ei', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_profile_mark'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Kyllä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_profile_mark'));

-- state tila
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 99, 'Ei tietoa', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_state'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Suunnitteilla', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_state'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Rakenteilla', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_state'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Käytössä pysyvästi', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_state'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Käytössä tilapäisesti', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_state'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Pois käytössä tilapäisesti', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_state'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 6, 'Poistuva pysyvä laite', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_state'));
-- jyrsitty
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 99, 'Ei tietoa', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_milled'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Ei', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_milled'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Pintamerkintä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_milled'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Siniaalto', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_milled'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Sylinterimerkintä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_milled'));

--herätemerkintä
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 99, 'Ei tietoa', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_rumble_strip'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Ei', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_rumble_strip'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Kyllä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'PT_rumble_strip'));