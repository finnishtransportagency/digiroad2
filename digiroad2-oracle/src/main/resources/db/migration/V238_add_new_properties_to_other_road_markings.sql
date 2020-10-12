-- ASSET_TYPE VALUE
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
	VALUES (480, 'Muut tiemerkinnät', 'point', 'db_migration_v238');



-- LOCALIZED_STRING VALUE
-- Asetusnumero
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Asetusnumero', 'db_migration_v238', SYSDATE);

-- Tiemerkinnän esittämän liikennemerkin luokka (pakollinen, jos merkintä kuvaa liikennemerkkiä)
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Tiemerkinnän esittämän liikennemerkin luokka', 'db_migration_v238', SYSDATE);

-- Tiemerkinnän tai tiemerkinnän esittämän liikennemerkin arvo (pakollinen, jos merkinnällä on arvo)
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Tiemerkinnän tai tiemerkinnän esittämän liikennemerkin arvo', 'db_migration_v238', SYSDATE);
	
-- Käännös (pakollinen tiemerkinnöille M1 ja M2)
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Käännös', 'db_migration_v238', SYSDATE);
	
-- Suhteellinen sijainti (valinnainen)
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Suhteellinen sijainti', 'db_migration_v238', SYSDATE);

-- Määrä (valinnainen)
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Määrä', 'db_migration_v238', SYSDATE);

-- Merkinnän materiaali (valinnainen, LOCALIZED_STRING-taulussa jo Merkin materiaali -value!!)
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Merkinnän materiaali', 'db_migration_v238', SYSDATE);

-- Merkinnän pituus (valinnainen, Tien leveys -value LOCALIZED_STRING-taulussa)
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Merkinnän pituus', 'db_migration_v238', SYSDATE);
	
-- Merkinnän leveys (valinnainen)
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Merkinnän leveys', 'db_migration_v238', SYSDATE);

-- Profiilimerkintä (valinnainen)
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Profiilimerkintä', 'db_migration_v238', SYSDATE);

-- Jyrsitty (valinnainen)
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Jyrsitty', 'db_migration_v238', SYSDATE);

-- Tila ja lisätieto poistettu (molemmat on jo olemassa LOCALIZED_STRING-taulussa)


-- Add properties

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 480, 'single_choice', 1, 'db_migration_v238', 'MT_regulatory_number',
        (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Asetusnumero'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 480, 'text', 0, 'db_migration_v238', 'MT_road_marking_class', 
			(SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Tiemerkinnän esittämän liikennemerkin luokka'));
		
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 480, 'text', 0, 'db_migration_v238', 'MT_road_marking_value',
			(SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Tiemerkinnän tai tiemerkinnän esittämän liikennemerkin arvo'));
		
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 480, 'number', 0, 'db_migration_v238', 'MT_road_marking_turn',
			(SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Käännös'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 480, 'number', 0, 'db_migration_v238', 'MT_road_marking_location',
			(SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Suhteellinen sijainti'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 480, 'single_choice', 0, 'db_migration_v238', 'MT_road_marking_condition',
			(SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Kunto'));
		
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 480, 'text', 0, 'db_migration_v238', 'MT_road_marking_quantity',
			(SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Määrä'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 480, 'single_choice', 0, 'db_migration_v238', 'MT_road_marking_material',
			(SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Merkinnän materiaali'));
		
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 480, 'number', 0, 'db_migration_v238', 'MT_road_marking_length',
			(SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Merkinnän pituus'));
		
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 480, 'number', 0, 'db_migration_v238', 'MT_road_marking_width',
			(SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Merkinnän leveys'));
		
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 480, 'single_choice', 0, 'db_migration_v238', 'MT_road_marking_profile_mark',
			(SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Profiilimerkintä'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 480, 'single_choice', 0, 'db_migration_v238', 'MT_road_marking_milled',
			(SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Jyrsitty'));
		
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 480, 'long_text', 0, 'db_migration_v238', 'MT_road_marking_additional_information',
			(SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Lisätieto'));
		
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 480, 'single_choice', 0, 'db_migration_v238', 'MT_road_marking_state',
			(SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Tila'));			
			

-- ENUMARATED VALUE ASETUSNUMERO
-- Tiemerkintöjen asetusnumero
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 1, 'M1 ajokaistanuoli', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 2, 'M2 ajokaistan vaihtamisnuoli', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
			
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 3, 'M3 Pysäköintialue', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
			
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 4, 'M4 Keltanen reunamerkintä', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
			
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 5, 'M5 Pysäyttämisrajoitus', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
			
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 6, 'M6 ohjausviiva', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
			
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 7, 'M7 Jalankulkija', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
			
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 8, 'M8 Pyöräilijä', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
			
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 9, 'M9 Väistämisvelvollisuutta osoittava ennakkomerkintä', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
			
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 10, 'M10 Stop-ennakkomerkintä', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 11, 'M11 P-Merkintä', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 12, 'M12 Invalidin ajoneuvo', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 13, 'M13 BUS-merkintä', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 14, 'M14 Taxi-merkintä', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 15, 'M15 Lataus', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 16, 'M16 Nopeusrajoitus', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 17, 'M17 Tienumero', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 18, 'M18 Risteysruudutus', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 19, 'M19 Liikennemerkki', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_regulatory_number'));
		

		
-- ENUMERATED VALUE CONDITION
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 99, 'Ei tietoa', '', 'db_migration_v238',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_condition'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 1,'Erittäin huono', '', 'db_migration_v238',
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_condition'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 2, 'Huono', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_condition'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 3, 'Tyydyttävä', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_condition'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 4, 'Hyvä', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_condition'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 5, 'Erittäin hyvä', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_condition'));
		
-- ENUMERATED VALUE MATERIAL
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 99, 'Ei tietoa', '', 'db_migration_v238',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_material'));
       
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Maali', '', 'db_migration_v238',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_material'));
       
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Massa', '', 'db_migration_v238',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_material'));
		
-- ENUMERATED VALUE PROFILE MARK
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 99, 'Ei tietoa', '', 'db_migration_v238',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_profile_mark'));
       
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Ei', '', 'db_migration_v238',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_profile_mark'));
       
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Kyllä', '', 'db_migration_v238',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_profile_mark'));
       
-- ENUMERATED VALUE JYRSITTY
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 99, 'Ei tietoa', '', 'db_migration_v238',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_milled'));
       
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Ei', '', 'db_migration_v238',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_milled'));
       
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Pintamerkintä', '', 'db_migration_v238',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_milled'));
       
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Siniaalto', '', 'db_migration_v238',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_milled'));
       
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Sylinterimerkintä', '', 'db_migration_v238',
        (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_milled'));
       
-- ENUMERATED VALUE STATE
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 1, 'Suunnitteilla ', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_state'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 2, 'Rakenteilla ', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_state'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 3, 'Käytössä pysyvästi (oletus)', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_state'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 4, 'Käytössä tilapäisesti', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_state'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 5, 'Pois käytössä tilapäisesti', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_state'));
		
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 6, 'Poistuva pysyvä laite', '', 'db_migration_v238', 
			(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'MT_road_marking_state'));
