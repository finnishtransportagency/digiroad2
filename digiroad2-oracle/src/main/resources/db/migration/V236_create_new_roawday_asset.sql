-- NEW ASSET TYPE
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
VALUES (450, 'K1 keskiviiva', 'linear', 'db_migration_v236');
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
VALUES (460, 'K2 ajokaistaviiva', 'linear', 'db_migration_v236');
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
VALUES (470, 'K3 Sulkuviiva', 'linear', 'db_migration_v236');
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
VALUES (480, 'K4 Varoitusviiva', 'linear', 'db_migration_v236');
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
VALUES (490, 'K5 Sulkualue', 'linear', 'db_migration_v236');
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
VALUES (500, 'K6 Reunaviiva', 'linear', 'db_migration_v236');

-- LOCALIZED_STRING VALUE
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'K1 keskiviiva', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'K2 ajokaistaviiva', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'K3 Sulkuviiva', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'K4 Varoitusviiva', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'K5 Sulkualue', 'db_migration_v236', SYSDATE);
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'K6 Reunaviiva', 'db_migration_v236', SYSDATE);

-- PROPERTY VALUE
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 450, 'text', 1, 'db_migration_v236', 'center_line',
        (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'K1 keskiviiva'));
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 460, 'text', 1, 'db_migration_v236', 'lane_line',
        (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'K2 ajokaistaviiva'));
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 470, 'text', 1, 'db_migration_v236', 'barrier_line',
        (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'K3 Sulkuviiva'));
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 480, 'text', 1, 'db_migration_v236', 'warning_line',
        (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'K4 Varoitusviiva'));
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 490, 'text', 1, 'db_migration_v236', 'closed_area',
        (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'K5 Sulkualue'));
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 500, 'text', 1, 'db_migration_v236', 'edge_line',
        (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'K6 Reunaviiva'));

-- ENUMARATED VALUE CONDITION K1

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
VALUES (primary_key_seq.nextval, 5, 'Erittäin hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'center_line'));

-- ENUMARATED VALUE CONDITION K2
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Erittäin huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'lane_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'lane_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Tyydyttävä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'lane_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'lane_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Erittäin hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'lane_line'));
-- ENUMARATED VALUE CONDITION K3
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Erittäin huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'barrier_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'barrier_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Tyydyttävä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'barrier_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'barrier_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Erittäin hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'barrier_line'));

-- ENUMARATED VALUE CONDITION K4
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Erittäin huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'warning_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'warning_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Tyydyttävä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'warning_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'warning_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Erittäin hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'warning_line'));

-- ENUMARATED VALUE CONDITION K5
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Erittäin huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'closed_area'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'closed_area'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Tyydyttävä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'closed_area'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'closed_area'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Erittäin hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'closed_area'));

-- ENUMARATED VALUE CONDITION K6
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Erittäin huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'edge_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Huono', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'edge_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Tyydyttävä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'edge_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'edge_line'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Erittäin hyvä', '', 'db_migration_v236',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'edge_line'));

create table ROADWAYS_LINE
(
    RAODWAY_ID                varchar(50) not null,
    REGULATORY_NUMBER varchar(50) NOT null,
    l_location        number      NOT NULL,
    STATE             number      NOT NULL,
    END_DATE          date        NOT NULL,
    ASSSET_TYP_ID     number,
    ENUMERATED_VALUE_ID,
    LANE_NUMBER       number,
    --LANE_TYPE         number, -- assetType
    MUNICIPAL_ID      varchar(50),
    --CONDITION         number,
    MATERIAL          number,
    LENGTH            number,
    WIDTH             number,
    RAISED            number,
    MILLED            number,
    ADDITIONAL_INFO   varchar(500),
    START_DATE        date,
    CONSTRAINT RAODWAY_ID_FK FOREIGN KEY (RAODWAY_ID) REFERENCES ASSET (ID),
    CONSTRAINT LANETYPE_FK FOREIGN KEY (ASSSET_TYP_ID) REFERENCES ASSET_TYPE (ID),
    CONSTRAINT CONDITION_FK FOREIGN KEY (ENUMERATED_VALUE_ID) REFERENCES ENUMERATED_VALUE (ID)
)