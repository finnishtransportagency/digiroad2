alter table property add default_value varchar2(256);

update property set default_value = '99' where public_id = 'pysakin_tyyppi';
update property set default_value = '99' where public_id = 'tietojen_yllapitaja';
update property set default_value = '99' where public_id = 'aikataulu';
update property set default_value = '99' where public_id = 'katos';
update property set default_value = '99' where public_id = 'mainoskatos';
update property set default_value = '99' where public_id = 'penkki';
update property set default_value = '99' where public_id = 'pyorateline';
update property set default_value = '99' where public_id = 'sahkoinen_aikataulunaytto';
update property set default_value = '99' where public_id = 'valaistus';
update property set default_value = '99' where public_id = 'saattomahdollisuus_henkiloautolla';