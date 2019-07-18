-- totalWeightLimit --
    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'kokonaispainorajoitus','db_migration_v210', sysdate);

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Kokonaispainorajoitukset'), 'integer', 1, 'db_migration_v210', 'weight', (select id from LOCALIZED_STRING where VALUE_FI = 'kokonaispaino rajoitus'));

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Kokonaispainorajoitukset'), 'checkbox', 0, 'db_migration_v210', 'suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (select id from asset_type where name = 'Kokonaispainorajoitukset')));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (select id from asset_type where name = 'Kokonaispainorajoitukset')));

-- trailerTruckWeightLimit --
    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'Ajoneuvoyhdistelmän painorajoitus', 'db_migration_v210', sysdate);

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Ajoneuvoyhdistelmän suurin sallittu massa'), 'integer', 1, 'db_migration_v210', 'weight', (select id from LOCALIZED_STRING where VALUE_FI = 'Ajoneuvoyhdistelmän painorajoitus'));

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Ajoneuvoyhdistelmän suurin sallittu massa'), 'checkbox', 0, 'db_migration_v210', 'suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (select id from asset_type where name = 'Ajoneuvoyhdistelmän suurin sallittu massa')));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (select id from asset_type where name = 'Ajoneuvoyhdistelmän suurin sallittu massa')));

-- axleWeightLimit --
    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'Ajoneuvon akselipainorajoitus', 'db_migration_v210', sysdate);

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Ajoneuvon suurin sallittu akselimassa'), 'integer', 1, 'db_migration_v210', 'weight', (select id from LOCALIZED_STRING where VALUE_FI = 'Ajoneuvon akselipainorajoitus'));

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Ajoneuvon suurin sallittu akselimassa'), 'checkbox', 0, 'db_migration_v210', 'suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (SELECT id FROM asset_type WHERE name = 'Ajoneuvon suurin sallittu akselimassa')));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (SELECT id FROM asset_type WHERE name = 'Ajoneuvon suurin sallittu akselimassa')));


-- heightLimit --
    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'Ajoneuvon korkeusrajoitus','db_migration_v210', sysdate);

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Ajoneuvon suurin sallittu korkeus'), 'integer', 1, 'db_migration_v210', 'height', (select id from LOCALIZED_STRING where VALUE_FI = 'Ajoneuvon korkeusrajoitus'));

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Ajoneuvon suurin sallittu korkeus'), 'checkbox', 0, 'db_migration_v210', 'suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (select id from asset_type where name = 'Ajoneuvon suurin sallittu korkeus')));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (select id from asset_type where name = 'Ajoneuvon suurin sallittu korkeus')));


-- lengthLimit --
    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'Ajoneuvon tai -yhdistelmän pituusrajoitus','db_migration_v210', sysdate);

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Ajoneuvon tai -yhdistelmän suurin sallittu pituus'), 'integer', 1, 'db_migration_v210', 'length', (select id from LOCALIZED_STRING where VALUE_FI = 'Ajoneuvon tai -yhdistelmän pituusrajoitus'));

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Ajoneuvon tai -yhdistelmän suurin sallittu pituus'), 'checkbox', 0, 'db_migration_v210', 'suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (SELECT id FROM asset_type WHERE name = 'Ajoneuvon tai -yhdistelmän suurin sallittu pituus')));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (SELECT id FROM asset_type WHERE name = 'Ajoneuvon tai -yhdistelmän suurin sallittu pituus')));


--  widthLimit --
    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'Ajoneuvon leveysrajoitus','db_migration_v210', sysdate);

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Ajoneuvon suurin sallittu leveys'), 'integer', 1, 'db_migration_v210', 'width', (select id from LOCALIZED_STRING where VALUE_FI = 'Ajoneuvon leveysrajoitus'));

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Ajoneuvon suurin sallittu leveys'), 'checkbox', 0, 'db_migration_v210', 'suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (select id from asset_type where name = 'Ajoneuvon suurin sallittu leveys')));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (select id from asset_type where name = 'Ajoneuvon suurin sallittu leveys')));


--  RoadWith --
    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'Tien leveys','db_migration_v210', sysdate);

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Tien leveys'), 'integer', 1, 'db_migration_v210', 'width', (select id from LOCALIZED_STRING where VALUE_FI = 'Tien leveys'));

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Tien leveys'), 'checkbox', 0, 'db_migration_v210', 'suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (select id from asset_type where name = 'Tien leveys')));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (select id from asset_type where name = 'Tien leveys')));

--  LitRoad --
    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Valaistu tie'), 'checkbox', 0, 'db_migration_v210', 'suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (select id from asset_type where name = 'Valaistu tie')));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v210', (select id from property where public_ID = 'suggest_box' and asset_type_id = (select id from asset_type where name = 'Valaistu tie')));

-- Convert existing properties
	UPDATE NUMBER_PROPERTY_VALUE num SET PROPERTY_ID = (SELECT ID FROM property p WHERE p.public_id = 'weight' and p.asset_type_id = 30) WHERE ASSET_ID IN (SELECT a.id FROM ASSET a
    JOIN NUMBER_PROPERTY_VALUE npv ON PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'mittarajoitus') AND a.id = npv.ASSET_ID
    WHERE A.ASSET_TYPE_ID = 30);

  UPDATE NUMBER_PROPERTY_VALUE num SET PROPERTY_ID = (SELECT ID FROM property p WHERE p.public_id = 'weight' and p.asset_type_id = 40) WHERE ASSET_ID IN (SELECT a.id FROM ASSET a
    JOIN NUMBER_PROPERTY_VALUE npv ON PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'mittarajoitus') AND a.id = npv.ASSET_ID
    WHERE A.ASSET_TYPE_ID = 40);

	UPDATE NUMBER_PROPERTY_VALUE num SET PROPERTY_ID = (SELECT ID FROM property p WHERE p.public_id = 'weight' and p.asset_type_id = 50) WHERE ASSET_ID IN (SELECT a.id FROM ASSET a
    JOIN NUMBER_PROPERTY_VALUE npv ON PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'mittarajoitus') AND a.id = npv.ASSET_ID
    WHERE A.ASSET_TYPE_ID = 50);

	UPDATE NUMBER_PROPERTY_VALUE num SET PROPERTY_ID = (SELECT ID FROM property p WHERE p.public_id = 'height' and p.asset_type_id = 70) WHERE ASSET_ID IN (SELECT a.id FROM ASSET a
    JOIN NUMBER_PROPERTY_VALUE npv ON PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'mittarajoitus') AND a.id = npv.ASSET_ID
    WHERE A.ASSET_TYPE_ID = 70);

	UPDATE NUMBER_PROPERTY_VALUE num SET PROPERTY_ID = (SELECT ID FROM property p WHERE p.public_id = 'length' and p.asset_type_id = 80) WHERE ASSET_ID IN (SELECT a.id FROM ASSET a
    JOIN NUMBER_PROPERTY_VALUE npv ON PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'mittarajoitus') AND a.id = npv.ASSET_ID
    WHERE A.ASSET_TYPE_ID = 80);

	UPDATE NUMBER_PROPERTY_VALUE num SET PROPERTY_ID = (SELECT ID FROM property p WHERE public_id = 'width' and p.asset_type_id = 90) WHERE ASSET_ID IN (SELECT a.id FROM ASSET a
    JOIN NUMBER_PROPERTY_VALUE npv ON PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'mittarajoitus') AND a.id = npv.ASSET_ID
    WHERE A.ASSET_TYPE_ID = 90);

  UPDATE NUMBER_PROPERTY_VALUE num SET PROPERTY_ID = (SELECT ID FROM property p WHERE p.public_id = 'width' and p.asset_type_id = 120) WHERE ASSET_ID IN (SELECT a.id FROM ASSET a
    JOIN NUMBER_PROPERTY_VALUE npv ON PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'mittarajoitus') AND a.id = npv.ASSET_ID
    WHERE A.ASSET_TYPE_ID = 120);
