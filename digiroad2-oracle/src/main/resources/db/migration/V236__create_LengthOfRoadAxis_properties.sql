
-- NEW ASSET TYPE
INSERT INTO ASSET_TYPE(ID, NAME, GEOMETRY_TYPE, CREATED_BY )
    VALUES (450,'Pituussuuntaiset tiemerkinnät','linear','db_migration_v236');
-- LOCALIZED_STRING VALUE


-- PROPERTY VALUE

-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 450, 'text', 1, 'db_migration_v236', 'lane_line',
--         (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'K2 ajokaistaviiva'));
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 450, 'text', 1, 'db_migration_v236', 'barrier_line',
--         (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'K3 Sulkuviiva'));
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 450, 'text', 1, 'db_migration_v236', 'warning_line',
--         (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'K4 Varoitusviiva'));
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 450, 'text', 1, 'db_migration_v236', 'closed_area',
--         (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'K5 Sulkualue'));
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 450, 'text', 1, 'db_migration_v236', 'edge_line',
--         (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'K6 Reunaviiva'));

-- ENUMARATED VALUE CONDITION

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Erittäin huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'center_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'center_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Tyydyttävä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'center_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'center_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 6, 'Erittäin hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'center_line'));

-- RoadMark asetusnumero
INSERT INTO ENUMERATED_VALUE (ID, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 'K1 keskiviiva', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'center_line'));
INSERT INTO ENUMERATED_VALUE (ID, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 'K2 ajokaistaviiva', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'center_line'));
INSERT INTO ENUMERATED_VALUE (ID, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 'K3 Sulkuviiva', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'center_line'));
INSERT INTO ENUMERATED_VALUE (ID, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 'K4 Varoitusviiva', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'center_line'));
INSERT INTO ENUMERATED_VALUE (ID, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 'K5 Sulkualue', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'center_line'));
INSERT INTO ENUMERATED_VALUE (ID, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 'K6 Reunaviiva', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'center_line'));
-- add propeties

-- localization
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Asetus numero', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Kaistannumero', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Kaistantyyppi', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Suhteellinen sijainti', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'id', 'db_migration_v236', SYSDATE);
--INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
--VALUES (primary_key_seq.nextval, 'kunto', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Merkinnän materiaali', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Merkinnän pituus', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Merkinnän leveys', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Profiilimerkintä', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Lisätieto', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Tila', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Loppu päivämäärä', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Alku päivämäärä', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Jyrsitty', 'db_migration_v236', SYSDATE);

--Create New Properties for New Asset K1
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 450, 'number', 1, 'db_migration_v236', 'asetus_num', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Asetus numero'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 450, 'number', 0, 'db_migration_v236', 'kaistanro', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Kaistannumero'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 450, 'string', 0, 'db_migration_v236', 'kaistatyyp', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'kaistatyyp'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 450, 'number', 1, 'db_migration_v236', 's_sijainti', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Suhteellinen sijainti'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 450, 'string', 0, 'db_migration_v236', 'id', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'id'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 450, 'string', 0, 'db_migration_v236', 'materiaali', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän materiaali'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 450, 'number', 0, 'db_migration_v236', 'pituus', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän pituus'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 450, 'number', 0, 'db_migration_v236', 'leveys', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän leveys'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 450, 'number', 0, 'db_migration_v236', 'koholla', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Profiilimerkintä'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 450, 'string', 0, 'db_migration_v236', 'lisatieto', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Lisätieto'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID,DEFAULT_VALUE)
VALUES (primary_key_seq.nextval, 450, 'number', 0, 'db_migration_v236', 'tila', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Tila'),3);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 450, 'string', 0, 'db_migration_v236', 'loppu_pvm', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Loppu päivämäärä'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 450, 'string', 0, 'db_migration_v236', 'alku_pvm', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Alku päivämäärä'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 450, 'string', 0, 'db_migration_v236', 'jyrsitty', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Jyrsitty'));

--Create New Properties for New Asset K2
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 460, 'number', 1, 'db_migration_v236', 'asetus_num', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'asetus_num'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 460, 'number', 0, 'db_migration_v236', 'kaistanro', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'kaistanro'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'kaistatyyp', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'kaistatyyp'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 460, 'number', 1, 'db_migration_v236', 's_sijainti', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Suhteellinen sijainti'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'id', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'id'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'materiaali', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän materiaali'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 460, 'number', 0, 'db_migration_v236', 'pituus', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän pituus'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 460, 'number', 0, 'db_migration_v236', 'leveys', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän leveys'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 460, 'number', 0, 'db_migration_v236', 'koholla', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Profiilimerkintä'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'lisatieto', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Lisätieto'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID,DEFAULT_VALUE)
-- VALUES (primary_key_seq.nextval, 460, 'number', 0, 'db_migration_v236', 'tila', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Tila'),3);
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'loppu_pvm', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'loppu_pvm'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'alku_pvm', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'alku_pvm'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'milled', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Jyrsitty'));
--
-- --Create New Properties for New Asset K3
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 470, 'number', 1, 'db_migration_v236', 'asetus_num', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'asetus_num'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 470, 'number', 0, 'db_migration_v236', 'kaistanro', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'kaistanro'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 470, 'string', 0, 'db_migration_v236', 'kaistatyyp', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'kaistatyyp'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 470, 'number', 1, 'db_migration_v236', 's_sijainti', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Suhteellinen sijainti'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 470, 'string', 0, 'db_migration_v236', 'id', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'id'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 470, 'string', 0, 'db_migration_v236', 'materiaali', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän materiaali'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 470, 'number', 0, 'db_migration_v236', 'pituus', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän pituus'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 470, 'number', 0, 'db_migration_v236', 'leveys', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän leveys'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 470, 'number', 0, 'db_migration_v236', 'koholla', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Profiilimerkintä'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 470, 'string', 0, 'db_migration_v236', 'lisatieto', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Lisätieto'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID,DEFAULT_VALUE)
-- VALUES (primary_key_seq.nextval, 470, 'number', 0, 'db_migration_v236', 'tila', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Tila'),3);
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 470, 'string', 0, 'db_migration_v236', 'loppu_pvm', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'loppu_pvm'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 470, 'string', 0, 'db_migration_v236', 'alku_pvm', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'alku_pvm'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 470, 'string', 0, 'db_migration_v236', 'milled', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Jyrsitty'));
--
-- --Create New Properties for New Asset K4
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 480, 'number', 1, 'db_migration_v236', 'asetus_num', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'asetus_num'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 480, 'number', 0, 'db_migration_v236', 'kaistanro', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'kaistanro'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 480, 'string', 0, 'db_migration_v236', 'kaistatyyp', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'kaistatyyp'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 480, 'number', 1, 'db_migration_v236', 's_sijainti', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Suhteellinen sijainti'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 480, 'string', 0, 'db_migration_v236', 'id', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'id'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 480, 'string', 0, 'db_migration_v236', 'materiaali', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän materiaali'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 480, 'number', 0, 'db_migration_v236', 'pituus', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän pituus'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 480, 'number', 0, 'db_migration_v236', 'leveys', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän leveys'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 480, 'number', 0, 'db_migration_v236', 'koholla', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Profiilimerkintä'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 480, 'string', 0, 'db_migration_v236', 'lisatieto', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Lisätieto'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID,DEFAULT_VALUE)
-- VALUES (primary_key_seq.nextval, 480, 'number', 0, 'db_migration_v236', 'tila', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Tila'),3);
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 480, 'string', 0, 'db_migration_v236', 'loppu_pvm', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'loppu_pvm'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 480, 'string', 0, 'db_migration_v236', 'alku_pvm', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'alku_pvm'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 480, 'string', 0, 'db_migration_v236', 'milled', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Jyrsitty'));
--
-- --Create New Properties for New Asset K5
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 490, 'number', 1, 'db_migration_v236', 'asetus_num', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'asetus_num'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 490, 'number', 0, 'db_migration_v236', 'kaistanro', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'kaistanro'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 490, 'string', 0, 'db_migration_v236', 'kaistatyyp', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'kaistatyyp'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 490, 'number', 1, 'db_migration_v236', 's_sijainti', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Suhteellinen sijainti'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 490, 'string', 0, 'db_migration_v236', 'id', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'id'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 490, 'string', 0, 'db_migration_v236', 'materiaali', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän materiaali'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 490, 'number', 0, 'db_migration_v236', 'pituus', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän pituus'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 490, 'number', 0, 'db_migration_v236', 'leveys', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän leveys'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 490, 'number', 0, 'db_migration_v236', 'koholla', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Profiilimerkintä'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 490, 'string', 0, 'db_migration_v236', 'lisatieto', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Lisätieto'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID,DEFAULT_VALUE)
-- VALUES (primary_key_seq.nextval, 490, 'number', 0, 'db_migration_v236', 'tila', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Tila'),3);
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 490, 'string', 0, 'db_migration_v236', 'loppu_pvm', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'loppu_pvm'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 490, 'string', 0, 'db_migration_v236', 'alku_pvm', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'alku_pvm'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 490, 'string', 0, 'db_migration_v236', 'milled', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Jyrsitty'));
--
-- --Create New Properties for New Asset K6
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 500, 'number', 1, 'db_migration_v236', 'asetus_num', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'asetus_num'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 500, 'number', 0, 'db_migration_v236', 'kaistanro', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'kaistanro'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 500, 'string', 0, 'db_migration_v236', 'kaistatyyp', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'kaistatyyp'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 500, 'number', 1, 'db_migration_v236', 's_sijainti', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Suhteellinen sijainti'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 500, 'string', 0, 'db_migration_v236', 'id', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'id'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 500, 'string', 0, 'db_migration_v236', 'materiaali', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän materiaali'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 500, 'number', 0, 'db_migration_v236', 'pituus', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän pituus'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 500, 'number', 0, 'db_migration_v236', 'leveys', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Merkinnän leveys'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 500, 'number', 0, 'db_migration_v236', 'koholla', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Profiilimerkintä'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 500, 'string', 0, 'db_migration_v236', 'lisatieto', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Lisätieto'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID,DEFAULT_VALUE)
-- VALUES (primary_key_seq.nextval, 500, 'number', 0, 'db_migration_v236', 'tila', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Tila'),3);
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 500, 'string', 0, 'db_migration_v236', 'loppu_pvm', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'loppu_pvm'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 500, 'string', 0, 'db_migration_v236', 'alku_pvm', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'alku_pvm'));
--
-- INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
-- VALUES (primary_key_seq.nextval, 500, 'string', 0, 'db_migration_v236', 'milled', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Jyrsitty'));

