update property set NAME_FI = 'Nimi suomeksi' where NAME_FI = 'Pysäkin nimi';

update property set NAME_FI = 'Tietojen ylläpitäjä' where NAME_FI = 'Ylläpitäjä';

update property set NAME_FI = 'Lisätiedot' where NAME_FI = 'Kommentit';

update property set NAME_FI = 'Palauteosoite' where NAME_FI = 'Ylläpitäjän sähköposti';

update property set NAME_FI = 'Matkustajatunnus' where NAME_FI = 'Pysäkin tunnus';

update property set NAME_FI = 'Liikennöintisuunta' where NAME_FI = 'Pysäkin suunta';

update property set NAME_FI = 'Esteettömyys liikuntarajoitteiselle' where NAME_FI = 'Esteettömyystiedot';

update property set NAME_FI = 'Varusteet (Katos)' where NAME_FI = 'Pysäkin katos';


delete from property where NAME_FI = 'Pysäkin saavutettavuus';


alter table property drop column name_sv;

alter table enumerated_value drop column name_sv;


insert into property (id, asset_type_id, created_by, name_fi, property_type)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Nimi ruotsiksi', 'text');

insert into property (id, asset_type_id, created_by, name_fi, property_type)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Maastokoordinaatti X', 'text');

insert into property (id, asset_type_id, created_by, name_fi, property_type)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Maastokoordinaatti Y', 'text');

insert into property (id, asset_type_id, created_by, name_fi, property_type)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Maastokoordinaatti Z', 'text');

insert into property (id, asset_type_id, created_by, name_fi, property_type)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Liikennöintisuuntima', 'read-only');


insert into property (id, asset_type_id, required, created_by, name_fi, property_type)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), '0', 'automatic_import', 'Varusteet (Aikataulu)', 'single_choice');

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 1, 'Ei', 'automatic_import', (select id from property where name_fi = 'Varusteet (Aikataulu)'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 2, 'Kyllä', 'automatic_import', (select id from property where name_fi = 'Varusteet (Aikataulu)'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 99, 'Ei tietoa', 'automatic_import', (select id from property where name_fi = 'Varusteet (Aikataulu)'));


insert into property (id, asset_type_id, required, created_by, name_fi, property_type)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), '0', 'automatic_import', 'Varusteet (Mainoskatos)', 'single_choice');

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 1, 'Ei', 'automatic_import', (select id from property where name_fi = 'Varusteet (Mainoskatos)'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 2, 'Kyllä', 'automatic_import', (select id from property where name_fi = 'Varusteet (Mainoskatos)'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 99, 'Ei tietoa', 'automatic_import', (select id from property where name_fi = 'Varusteet (Mainoskatos)'));


insert into property (id, asset_type_id, required, created_by, name_fi, property_type)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), '0', 'automatic_import', 'Varusteet (Penkki)', 'single_choice');

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 1, 'Ei', 'automatic_import', (select id from property where name_fi = 'Varusteet (Penkki)'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 2, 'Kyllä', 'automatic_import', (select id from property where name_fi = 'Varusteet (Penkki)'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 99, 'Ei tietoa', 'automatic_import', (select id from property where name_fi = 'Varusteet (Penkki)'));


insert into property (id, asset_type_id, required, created_by, name_fi, property_type)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), '0', 'automatic_import', 'Varusteet (Pyöräteline)', 'single_choice');

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 1, 'Ei', 'automatic_import', (select id from property where name_fi = 'Varusteet (Pyöräteline)'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 2, 'Kyllä', 'automatic_import', (select id from property where name_fi = 'Varusteet (Pyöräteline)'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 99, 'Ei tietoa', 'automatic_import', (select id from property where name_fi = 'Varusteet (Pyöräteline)'));


insert into property (id, asset_type_id, required, created_by, name_fi, property_type)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), '0', 'automatic_import', 'Varusteet (Sähköinen aikataulunäyttö)', 'single_choice');

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 1, 'Ei', 'automatic_import', (select id from property where name_fi = 'Varusteet (Sähköinen aikataulunäyttö)'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 2, 'Kyllä', 'automatic_import', (select id from property where name_fi = 'Varusteet (Sähköinen aikataulunäyttö)'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 99, 'Ei tietoa', 'automatic_import', (select id from property where name_fi = 'Varusteet (Sähköinen aikataulunäyttö)'));


insert into property (id, asset_type_id, required, created_by, name_fi, property_type)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), '0', 'automatic_import', 'Varusteet (Valaistus)', 'single_choice');

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 1, 'Ei', 'automatic_import', (select id from property where name_fi = 'Varusteet (Valaistus)'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 2, 'Kyllä', 'automatic_import', (select id from property where name_fi = 'Varusteet (Valaistus)'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 99, 'Ei tietoa', 'automatic_import', (select id from property where name_fi = 'Varusteet (Valaistus)'));


insert into property (id, asset_type_id, required, created_by, name_fi, property_type)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), '0', 'automatic_import', 'Saattomahdollisuus henkilöautolla', 'single_choice');

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 1, 'Ei', 'automatic_import', (select id from property where name_fi = 'Saattomahdollisuus henkilöautolla'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 2, 'Kyllä', 'automatic_import', (select id from property where name_fi = 'Saattomahdollisuus henkilöautolla'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 99, 'Ei tietoa', 'automatic_import', (select id from property where name_fi = 'Saattomahdollisuus henkilöautolla'));


insert into property (id, asset_type_id, created_by, name_fi, property_type)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Liityntäpysäköintipaikkojen määrä', 'text');

insert into property (id, asset_type_id, created_by, name_fi, property_type)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), 'automatic_import', 'Pysäkin omistaja', 'text');
