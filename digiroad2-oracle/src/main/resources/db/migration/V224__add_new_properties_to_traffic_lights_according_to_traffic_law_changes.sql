--Add property traffic light type -> Tyyppi
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'single_choice', 1, 'db_migration_v224', 'trafficLight_type', (select id from LOCALIZED_STRING where VALUE_FI = 'Tyyppi'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Valo-opastin', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4.1, 'Nuolivalo oikealle', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4.2, 'Nuolivalo vasemmalle', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Kolmio-opastin', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 8, 'Joukkoliikenneopastin', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 9.1, 'Polkupyöräopastin', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 9.2, 'Polkupyörän nuolivalo', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 10, 'Jalankulkijan opastin', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 11, 'Ajokaistaopastin', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_type'));

--Add property traffic light relative position of the device -> Opastimen suhteellinen sijainti
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Opastimen suhteellinen sijainti','db_migration_v224', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'single_choice', 0, 'db_migration_v224', 'trafficLight_relative_position', (select id from LOCALIZED_STRING where VALUE_FI = 'Opastimen suhteellinen sijainti'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Ajoradan oikea puoli (ajosuuntaan nähden)', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_relative_position'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Kaistojen yläpuolella', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_relative_position'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Keskisaareke tai liikenteenjakaja', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_relative_position'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tiedossa', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_relative_position'));

--Add property traffic light structure -> Opastimen rakennelma
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Opastimen rakennelma','db_migration_v224', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'single_choice', 0, 'db_migration_v224', 'trafficLight_structure', (select id from LOCALIZED_STRING where VALUE_FI = 'Opastimen rakennelma'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Pylväs', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_structure'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Seinä', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_structure'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Silta', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_structure'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Portaali', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_structure'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Puoliportaali', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_structure'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 6, 'Puomi tai muu esterakennelma', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_structure'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 7, 'Muu', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_structure'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tiedossa', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_structure'));

--Add property traffic light maximum height for passing under -> Alituskorkeus
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Alituskorkeus','db_migration_v224', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'number', 0, 'db_migration_v224', 'trafficLight_height', (select id from LOCALIZED_STRING where VALUE_FI = 'Alituskorkeus'));

--Add property traffic light sound signal -> Äänimerkki
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Äänimerkki','db_migration_v224', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'single_choice', 0, 'db_migration_v224', 'trafficLight_sound_signal', (select id from LOCALIZED_STRING where VALUE_FI = 'Äänimerkki'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Ei', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_sound_signal'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Kyllä', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_sound_signal'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tiedossa', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_sound_signal'));

--Add property traffic light detection of vehicle) -> Ajoneuvon tunnistus
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Ajoneuvon tunnistus','db_migration_v224', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'single_choice', 0, 'db_migration_v224', 'trafficLight_vehicle_detection', (select id from LOCALIZED_STRING where VALUE_FI = 'Ajoneuvon tunnistus'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Silmukka', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_vehicle_detection'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Infrapunailmaisin', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_vehicle_detection'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Tutka eli mikroaaltoilmaisin', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_vehicle_detection'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Muu', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_vehicle_detection'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tiedossa', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_vehicle_detection'));

--Add property traffic light push button -> Painonappi
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Painonappi','db_migration_v224', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'single_choice', 0, 'db_migration_v224', 'trafficLight_push_button', (select id from LOCALIZED_STRING where VALUE_FI = 'Painonappi'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Ei', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_push_button'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Kyllä', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_push_button'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tiedossa', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_push_button'));

--Add property traffic light additional information -> Lisätieto
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'text', 0, 'db_migration_v224', 'trafficLight_info', (select id from LOCALIZED_STRING where VALUE_FI = 'Lisätieto'));

--Add property traffic light lane -> Kaista
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'number', 0, 'db_migration_v224', 'trafficLight_lane', (select id from LOCALIZED_STRING where VALUE_FI = 'Kaista'));

--Add property traffic light type -> Kaistan Tyyppi
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'single_choice', 0, 'db_migration_v224', 'trafficLight_lane_type', (select id from LOCALIZED_STRING where VALUE_FI = 'Kaistan tyyppi'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Pääkaista', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Ohituskaista', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Kääntymiskaista oikealle', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Kääntymiskaista vasemmalle', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Lisäkaista suoraan ajaville', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 6, 'Liittymiskaista', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 7, 'Erkanemiskaista', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 8, 'Sekoittumiskaista', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 9, 'Joukkoliikenteen kaista / taksikaista', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 10, 'Raskaan liikenteen kaista', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 11, 'Vaihtuvasuuntainen kaista', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 20, 'Yhdistetty jalankulun ja pyöräilyn kaista', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 21, 'Jalankulun kaista', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 22, 'Pyöräilykaista', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tiedossa', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_lane_type'));

--Add property traffic light location coordinates -> Maastosijainti
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Maastosijainti X','db_migration_v224', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'number', 0, 'db_migration_v224', 'location_coordinates_x', (select id from LOCALIZED_STRING where VALUE_FI = 'Maastosijainti X'));

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Maastosijainti Y','db_migration_v224', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'number', 0, 'db_migration_v224', 'location_coordinates_y', (select id from LOCALIZED_STRING where VALUE_FI = 'Maastosijainti Y'));

--Add property traffic light municipality id -> Kunta ID
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Kunta ID','db_migration_v224', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'text', 0, 'db_migration_v224', 'trafficLight_municipality_id', (select id from LOCALIZED_STRING where VALUE_FI = 'Kunta ID'));

--Add property traffic light State -> Tila
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'single_choice', 0, 'db_migration_v224', 'trafficLight_state', (select id from LOCALIZED_STRING where VALUE_FI = 'Tila'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Suunnitteilla', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_state'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Rakenteilla', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_state'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Käytössä pysyvästi', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_state'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Käytössä tilapäisesti', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_state'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Pois käytöstä tilapaisesti', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_state'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 6, 'Poistuva pysyvä laite', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_state'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tiedossa', ' ', 'db_migration_v224', (select id from property where public_ID = 'trafficLight_state'));

--grouped Id modifications
CREATE SEQUENCE grouped_id_seq
  minvalue 1
  maxvalue 999999999999999999999999999
  start with 1
  increment by 1
  cache 100
  nocycle;

--Adding GroupedId Column
ALTER TABLE MULTIPLE_CHOICE_VALUE
ADD GROUPED_ID NUMBER DEFAULT 0;

ALTER TABLE TEXT_PROPERTY_VALUE
ADD GROUPED_ID NUMBER DEFAULT 0;

ALTER TABLE SINGLE_CHOICE_VALUE
ADD GROUPED_ID NUMBER DEFAULT 0;

ALTER TABLE NUMBER_PROPERTY_VALUE
ADD GROUPED_ID NUMBER DEFAULT 0;

--Alter Constraints
ALTER TABLE SINGLE_CHOICE_VALUE
DROP CONSTRAINT SINGLE_CHOICE_PK;

ALTER TABLE SINGLE_CHOICE_VALUE
ADD CONSTRAINT SINGLE_CHOICE_PK PRIMARY KEY (ASSET_ID, ENUMERATED_VALUE_ID, GROUPED_ID);

DROP INDEX AID_PID_TEXT_PROPERTY_SX;

CREATE UNIQUE INDEX AID_PID_TEXT_PROPERTY_SX ON TEXT_PROPERTY_VALUE ("ASSET_ID", "PROPERTY_ID", "GROUPED_ID");