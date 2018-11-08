CREATE TABLE additional_panel(
	id number,
	asset_id number references asset not null,
	property_id references property not null,
	traffic_sign_type number not null,
	traffic_sign_value number,
	traffic_sign_info varchar(128),
	form_position number not null
);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Lisäkilpi','db_migration_v186', sysdate);

INSERT INTO PROPERTY (ID,ASSET_TYPE_ID,PROPERTY_TYPE,REQUIRED, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'),'additional_panel',0, 'db_migration_v186', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Lisäkilpi'),'additional_panel_type');
