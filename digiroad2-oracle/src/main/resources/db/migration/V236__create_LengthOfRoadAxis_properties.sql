-- NEW ASSET TYPE
INSERT INTO ASSET_TYPE(ID, NAME, GEOMETRY_TYPE, CREATED_BY)
VALUES (460, 'Pituussuuntaiset tiemerkinnät', 'linear', 'db_migration_v236');

-- LOCALIZED_STRING VALUE
DECLARE
    foundItem VARCHAR2(256);
BEGIN
    SELECT COUNT(*) INTO foundItem FROM LOCALIZED_STRING WHERE VALUE_FI = 'Asetusnumero';
    IF foundItem = 0 THEN
        INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
        VALUES (primary_key_seq.nextval, 'Asetusnumero', 'db_migration_v236', SYSDATE);
    END IF;

    SELECT COUNT(*) INTO foundItem FROM LOCALIZED_STRING WHERE VALUE_FI = 'Kaistannumero';
    IF foundItem = 0 THEN
        INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
        VALUES (primary_key_seq.nextval, 'Kaistannumero', 'db_migration_v236', SYSDATE);
    END IF;

    SELECT COUNT(*) INTO foundItem FROM LOCALIZED_STRING WHERE VALUE_FI = 'Kaistantyyppi';
    IF foundItem = 0 THEN
        INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
        VALUES (primary_key_seq.nextval, 'Kaistantyyppi', 'db_migration_v236', SYSDATE);
    END IF;

    SELECT COUNT(*) INTO foundItem FROM LOCALIZED_STRING WHERE VALUE_FI = 'Suhteellinen sijainti';
    IF foundItem = 0 THEN
        INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
        VALUES (primary_key_seq.nextval, 'Suhteellinen sijainti', 'db_migration_v236', SYSDATE);
    END IF;

    SELECT COUNT(*) INTO foundItem FROM LOCALIZED_STRING WHERE VALUE_FI = 'Merkinnän materiaali';
    IF foundItem = 0 THEN
        INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
        VALUES (primary_key_seq.nextval, 'Merkinnän materiaali', 'db_migration_v236', SYSDATE);

    END IF;

    SELECT COUNT(*) INTO foundItem FROM LOCALIZED_STRING WHERE VALUE_FI = 'Merkinnän pituus';
    IF foundItem = 0 THEN
        INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
        VALUES (primary_key_seq.nextval, 'Merkinnän pituus', 'db_migration_v236', SYSDATE);
    END IF;

    SELECT COUNT(*) INTO foundItem FROM LOCALIZED_STRING WHERE VALUE_FI = 'Merkinnän leveys';
    IF foundItem = 0 THEN
        INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
        VALUES (primary_key_seq.nextval, 'Merkinnän leveys', 'db_migration_v236', SYSDATE);
    END IF;

    SELECT COUNT(*) INTO foundItem FROM LOCALIZED_STRING WHERE VALUE_FI = 'Profiilimerkintä';
    IF foundItem = 0 THEN
        INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
        VALUES (primary_key_seq.nextval, 'Profiilimerkintä', 'db_migration_v236', SYSDATE);
    END IF;

    SELECT COUNT(*) INTO foundItem FROM LOCALIZED_STRING WHERE VALUE_FI = 'Lisätieto';
    IF foundItem = 0 THEN
        INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
        VALUES (primary_key_seq.nextval, 'Lisätieto', 'db_migration_v236', SYSDATE);

    END IF;

    SELECT COUNT(*) INTO foundItem FROM LOCALIZED_STRING WHERE VALUE_FI = 'Tila';
    IF foundItem = 0 THEN
        INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
        VALUES (primary_key_seq.nextval, 'Tila', 'db_migration_v236', SYSDATE);
    END IF;

    SELECT COUNT(*) INTO foundItem FROM LOCALIZED_STRING WHERE VALUE_FI = 'Loppu päivämäärä';
    IF foundItem = 0 THEN
        INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
        VALUES (primary_key_seq.nextval, 'Loppu päivämäärä', 'db_migration_v236', SYSDATE);
    END IF;

    SELECT COUNT(*) INTO foundItem FROM LOCALIZED_STRING WHERE VALUE_FI = 'Alku päivämäärä';
    IF foundItem = 0 THEN
        INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
        VALUES (primary_key_seq.nextval, 'Alku päivämäärä', 'db_migration_v236', SYSDATE);
    END IF;

    SELECT COUNT(*) INTO foundItem FROM LOCALIZED_STRING WHERE VALUE_FI = 'Jyrsitty';
    IF foundItem = 0 THEN
        INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
        VALUES (primary_key_seq.nextval, 'Jyrsitty', 'db_migration_v236', SYSDATE);
    END IF;
    SELECT COUNT(*) INTO foundItem FROM LOCALIZED_STRING WHERE VALUE_FI = 'Kuntanumero';
    IF foundItem = 0 THEN
        INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
        VALUES (primary_key_seq.nextval, 'Kuntanumero', 'db_migration_v236', SYSDATE);
    END IF;
    SELECT COUNT(*) INTO foundItem FROM LOCALIZED_STRING WHERE VALUE_FI = 'Kunto';
    IF foundItem = 0 THEN
        INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
        VALUES (primary_key_seq.nextval, 'Kunto', 'db_migration_v236', SYSDATE);
    END IF;
END;

-- add propeties
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'single_choice', 1, 'db_migration_v236', 'regulatory_number',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Asetusnumero'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'number', 0, 'db_migration_v236', 'lane_number',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Kaistannumero'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'lane_type',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Kaistantyyppi'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'number', 1, 'db_migration_v236', 'lane_location',
        (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Suhteellinen sijainti'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'municipal_id',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Kuntanumero'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'material',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Merkinnän materiaali'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'number', 0, 'db_migration_v236', 'length',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Merkinnän pituus'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'number', 0, 'db_migration_v236', 'width',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Merkinnän leveys'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'number', 0, 'db_migration_v236', 'raised',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Profiilimerkintä'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'additional_information',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Lisätieto'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID,
                      DEFAULT_VALUE)
VALUES (primary_key_seq.nextval, 460, 'number', 0, 'db_migration_v236', 'state',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Tila'), 3);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'end_date',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Loppu päivämäärä'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'start_date',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Alku päivämäärä'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'string', 0, 'db_migration_v236', 'milled',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Jyrsitty'));
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'number', 0, 'db_migration_v236', 'condition',
        (select id from LOCALIZED_STRING where VALUE_FI = 'Kunto'));

-- ENUMARATED VALUE CONDITION

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Erittäin huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'condition'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'condition'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Tyydyttävä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'condition'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'condition'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 6, 'Erittäin hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'condition'));
--material
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Maali', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'material'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Massa', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'material'));

-- RoadMark asetusnumero
INSERT INTO ENUMERATED_VALUE (ID, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 'K1 keskiviiva', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'regulatory_number'));
INSERT INTO ENUMERATED_VALUE (ID, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 'K2 ajokaistaviiva', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'regulatory_number'));
INSERT INTO ENUMERATED_VALUE (ID, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 'K3 Sulkuviiva', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'regulatory_number'));
INSERT INTO ENUMERATED_VALUE (ID, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 'K4 Varoitusviiva', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'regulatory_number'));
INSERT INTO ENUMERATED_VALUE (ID, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 'K5 Sulkualue', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'regulatory_number'));
INSERT INTO ENUMERATED_VALUE (ID, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 'K6 Reunaviiva', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'regulatory_number'));