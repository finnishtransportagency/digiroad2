-- Pysäkkityyppi
insert into asset_type (id, name, geometry_type, created_date, created_by, modified_date, modified_by) values (10, 'Bussipysäkit', 'point', current_timestamp, 'automatic_import', null, null);

-- Pysäkin katos
insert into property (id, asset_type_id, required, created_by, name_fi, name_sv, property_type)
values (1, (select id from asset_type where name = 'Bussipysäkit'), '1', 'automatic_import', 'Pysäkin katos', ' ', 'single_choice');

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (1, 1, 'Ei', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin katos'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (2, 2, 'Kyllä', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin katos'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (3, 99, 'Ei tietoa', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin katos'));

-- Pysäkin tyyppi
insert into property (id, asset_type_id, required, created_by, name_fi, name_sv, property_type)
values (2, (select id from asset_type where name = 'Bussipysäkit'), '1', 'automatic_import', 'Pysäkin tyyppi', ' ', 'multiple_choice');

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (4, 1, 'Raitiovaunu', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin tyyppi'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (5, 2, 'Linja-autojen paikallisliikenne', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin tyyppi'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (6, 3, 'Linja-autojen kaukoliikenne', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin tyyppi'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (7, 4, 'Linja-autojen pikavuoro', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin tyyppi'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (8, 99, 'Ei tietoa', ' ', 'automatic_import', (select id from property where name_fi = 'Pysäkin tyyppi'));


-- Ylläpitäjä
insert into property (id, asset_type_id, required, created_by, name_fi, name_sv, property_type)
values (3, (select id from asset_type where name = 'Bussipysäkit'), '1', 'automatic_import', 'Ylläpitäjä', ' ', 'single_choice');

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (9, 1, 'Kunta', ' ', 'automatic_import', (select id from property where name_fi = 'Ylläpitäjä'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (10, 2, 'ELY-keskus', ' ', 'automatic_import', (select id from property where name_fi = 'Ylläpitäjä'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (11, 3, 'Helsingin seudun liikenne', ' ', 'automatic_import', (select id from property where name_fi = 'Ylläpitäjä'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (12, 4, 'Liikennevirasto', ' ', 'automatic_import', (select id from property where name_fi = 'Ylläpitäjä'));


-- Pysäkin saavutettavuus
insert into property (id, asset_type_id, created_by, name_fi, name_sv, property_type)
values (4, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Pysäkin saavutettavuus', ' ', 'text');


-- Esteettömyystiedot
insert into property (id, asset_type_id, created_by, name_fi, name_sv, property_type)
values (5, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Esteettömyystiedot', ' ', 'text');


-- Ylläpitäjän tunnus
insert into property (id, asset_type_id, created_by, name_fi, name_sv, property_type)
values (6, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Ylläpitäjän tunnus', ' ', 'text');


-- Ylläpitäjän sähköposti
insert into property (id, asset_type_id, created_by, name_fi, name_sv, property_type)
values (7, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Ylläpitäjän sähköposti', ' ', 'text');


-- Kommentit
insert into property (id, asset_type_id, created_by, name_fi, name_sv, property_type)
values (10, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Kommentit', ' ', 'text');
