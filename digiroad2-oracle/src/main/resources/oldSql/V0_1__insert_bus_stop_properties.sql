-- Pysäkkityyppi
insert into asset_type (id, name, geometry_type, created_date, created_by, modified_date, modified_by) values (10, 'Bussipysäkit', 'point', current_timestamp, 'automatic_import', null, null);


-- Pysäkin tunnus
insert into property (id, asset_type_id, created_by, name_fi, name_sv, property_type)
values (70, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Pysäkin tunnus', ' ', 'text');


-- Pysäkin nimi
insert into property (id, asset_type_id, created_by, name_fi, name_sv, property_type)
values (80, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Pysäkin nimi', ' ', 'text');


-- Pysäkin suunta
insert into property (id, asset_type_id, created_by, name_fi, name_sv, property_type)
values (90, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Pysäkin suunta', ' ', 'text');


-- Pysäkin katos
insert into property (id, asset_type_id, required, created_by, name_fi, name_sv, property_type)
values (100, (select id from asset_type where name = 'Bussipysäkit'), '1', 'automatic_import', 'Pysäkin katos', ' ', 'single_choice');

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (101, 1, 'Ei', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin katos'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (102, 2, 'Kyllä', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin katos'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (103, 99, 'Ei tietoa', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin katos'));

-- Pysäkin tyyppi
insert into property (id, asset_type_id, required, created_by, name_fi, name_sv, property_type)
values (200, (select id from asset_type where name = 'Bussipysäkit'), '1', 'automatic_import', 'Pysäkin tyyppi', ' ', 'multiple_choice');

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (204, 1, 'Raitiovaunu', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin tyyppi'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (205, 2, 'Linja-autojen paikallisliikenne', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin tyyppi'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (206, 3, 'Linja-autojen kaukoliikenne', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin tyyppi'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (207, 4, 'Linja-autojen pikavuoro', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin tyyppi'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (208, 99, 'Ei tietoa', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin tyyppi'));


-- Ylläpitäjä
insert into property (id, asset_type_id, required, created_by, name_fi, name_sv, property_type)
values (300, (select id from asset_type where name = 'Bussipysäkit'), '1', 'automatic_import', 'Ylläpitäjä', ' ', 'single_choice');

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (309, 1, 'Kunta', ' ', 'automatic_import', (select id from property where name_fi = 'Ylläpitäjä'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (310, 2, 'ELY-keskus', ' ', 'automatic_import', (select id from property where name_fi = 'Ylläpitäjä'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (311, 3, 'Helsingin seudun liikenne', ' ', 'automatic_import', (select id from property where name_fi = 'Ylläpitäjä'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (312, 4, 'Liikennevirasto', ' ', 'automatic_import', (select id from property where name_fi = 'Ylläpitäjä'));


-- Ylläpitäjän tunnus
insert into property (id, asset_type_id, created_by, name_fi, name_sv, property_type)
values (400, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Ylläpitäjän tunnus', ' ', 'text');


-- Ylläpitäjän sähköposti
insert into property (id, asset_type_id, created_by, name_fi, name_sv, property_type)
values (500, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Ylläpitäjän sähköposti', ' ', 'text');


-- Pysäkin saavutettavuus
insert into property (id, asset_type_id, created_by, name_fi, name_sv, property_type)
values (600, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Pysäkin saavutettavuus', ' ', 'long_text');


-- Esteettömyystiedot
insert into property (id, asset_type_id, created_by, name_fi, name_sv, property_type)
values (700, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Esteettömyystiedot', ' ', 'long_text');


-- Kommentit
insert into property (id, asset_type_id, created_by, name_fi, name_sv, property_type)
values (800, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Kommentit', ' ', 'long_text');
