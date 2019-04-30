    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'Vihjetieto','db_migration_v199', sysdate);

-- totalWeightLimit --
    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'kokonaispainorajoitus'','db_migration_v199', sysdate);

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Kokonaispainorajoitukset'), 'integer', 1, 'db_migration_v199', 'total_weight', (select id from LOCALIZED_STRING where VALUE_FI = 'kokonaispaino rajoitus'));

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Kokonaispainorajoitukset'), 'checkbox', 0, 'db_migration_v199', 'total_weight_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'total_weight_suggest_box'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'total_weight_suggest_box'));

-- trailerTruckWeightLimit --
    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)

    VALUES (primary_key_seq.nextval,'Ajoneuvoyhdistelmän painorajoitus','db_migration_v199', sysdate);
    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Ajoneuvoyhdistelmän suurin sallittu massa'), 'integer', 1, 'db_migration_v199', 'trailer_truck_weight', (select id from LOCALIZED_STRING where VALUE_FI = 'Ajoneuvoyhdistelmän painorajoitus'));

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Ajoneuvoyhdistelmän suurin sallittu massa'), 'checkbox', 0, 'db_migration_v199', 'trailer_truck_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'trailer_truck_suggest_box'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'trailer_truck_suggest_box'));

-- axleWeightLimit --
    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'Ajoneuvon akselipainorajoitus', 'db_migration_v199', sysdate);

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Ajoneuvon suurin sallittu akselimassa'), 'integer', 1, 'db_migration_v199', 'axle_weight', (select id from LOCALIZED_STRING where VALUE_FI = 'Ajoneuvon akselipainorajoitus'));

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Ajoneuvon suurin sallittu akselimassa'), 'checkbox', 0, 'db_migration_v199', 'axle_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'axle_suggest_box'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'axle_suggest_box'));


-- heightLimit --
    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'Ajoneuvon korkeusrajoitus','db_migration_v199', sysdate);

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Ajoneuvon suurin sallittu korkeus'), 'integer', 1, 'db_migration_v199', 'height', (select id from LOCALIZED_STRING where VALUE_FI = 'Ajoneuvon korkeusrajoitus'));

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Ajoneuvon suurin sallittu korkeus'), 'checkbox', 0, 'db_migration_v199', 'height_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'height_suggest_box'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'height_suggest_box'));


-- lengthLimit --
    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'Ajoneuvon tai -yhdistelmän pituusrajoitus','db_migration_v199', sysdate);

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Ajoneuvon tai -yhdistelmän suurin sallittu pituus'), 'integer', 1, 'db_migration_v199', 'length', (select id from LOCALIZED_STRING where VALUE_FI = 'Ajoneuvon tai -yhdistelmän pituusrajoitus'));

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Ajoneuvon tai -yhdistelmän suurin sallittu pituus'), 'checkbox', 0, 'db_migration_v199', 'length_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'length_suggest_box' and asset_type_id = 80));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'length_suggest_box' and asset_type_id = 80));


--  widthLimit --
    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'Ajoneuvon leveysrajoitus','db_migration_v199', sysdate);

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Ajoneuvon suurin sallittu leveys'), 'integer', 1, 'db_migration_v199', 'width', (select id from LOCALIZED_STRING where VALUE_FI = 'Ajoneuvon leveysrajoitus'));

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Ajoneuvon suurin sallittu leveys'), 'checkbox', 0, 'db_migration_v199', 'width_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'width_suggest_box'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'width_suggest_box'));


--  RoadWith --
    INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval,'Tien leveys','db_migration_v199', sysdate);

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (SELECT id FROM asset_type WHERE name = 'Tien leveys'), 'integer', 1, 'db_migration_v199', 'road_width', (select id from LOCALIZED_STRING where VALUE_FI = 'Tien leveys'));

    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Tien leveys'), 'checkbox', 0, 'db_migration_v199', 'road_width_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'road_width_suggest_box'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'road_width_suggest_box'));


    --  LitRoad --
    INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Valaistu tie'), 'checkbox', 0, 'db_migration_v199', 'lit_road_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'lit_road_suggest_box'));

    INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
    VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'lit_road_suggest_box'));

-- Convert existing properties
	UPDATE NUMBER_PROPERTY_VALUE num SET PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'total_weight') WHERE ASSET_ID IN (SELECT a.id FROM ASSET a
    JOIN NUMBER_PROPERTY_VALUE npv ON PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'mittarajoitus') AND a.id = npv.ASSET_ID
    WHERE A.ASSET_TYPE_ID = 30);

    UPDATE NUMBER_PROPERTY_VALUE num SET PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'trailer_truck_weight') WHERE ASSET_ID IN (SELECT a.id FROM ASSET a
    JOIN NUMBER_PROPERTY_VALUE npv ON PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'mittarajoitus') AND a.id = npv.ASSET_ID
    WHERE A.ASSET_TYPE_ID = 40);

	UPDATE NUMBER_PROPERTY_VALUE num SET PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'axle_weight') WHERE ASSET_ID IN (SELECT a.id FROM ASSET a
    JOIN NUMBER_PROPERTY_VALUE npv ON PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'mittarajoitus') AND a.id = npv.ASSET_ID
    WHERE A.ASSET_TYPE_ID = 50);

	UPDATE NUMBER_PROPERTY_VALUE num SET PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'height') WHERE ASSET_ID IN (SELECT a.id FROM ASSET a
    JOIN NUMBER_PROPERTY_VALUE npv ON PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'mittarajoitus') AND a.id = npv.ASSET_ID
    WHERE A.ASSET_TYPE_ID = 70);

	UPDATE NUMBER_PROPERTY_VALUE num SET PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'length') WHERE ASSET_ID IN (SELECT a.id FROM ASSET a
    JOIN NUMBER_PROPERTY_VALUE npv ON PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'mittarajoitus') AND a.id = npv.ASSET_ID
    WHERE A.ASSET_TYPE_ID = 80);

	UPDATE NUMBER_PROPERTY_VALUE num SET PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'width') WHERE ASSET_ID IN (SELECT a.id FROM ASSET a
    JOIN NUMBER_PROPERTY_VALUE npv ON PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'mittarajoitus') AND a.id = npv.ASSET_ID
    WHERE A.ASSET_TYPE_ID = 90);

    UPDATE NUMBER_PROPERTY_VALUE num SET PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'road_width') WHERE ASSET_ID IN (SELECT a.id FROM ASSET a
    JOIN NUMBER_PROPERTY_VALUE npv ON PROPERTY_ID = (SELECT ID FROM property WHERE public_id = 'mittarajoitus') AND a.id = npv.ASSET_ID
    WHERE A.ASSET_TYPE_ID = 120);
