--,3067)); ST_GeomFromText('POINT(317882 6829768 0 0)',3067);
DECLARE
  asset_id number;
  service_point_value_id number;
    BEGIN
      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(435030 6902586 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(434386 6901319 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(435133 6901627 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(441251 6902347 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(434918 6901773 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(458338 6903268 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(431416 6900405 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tikkakoski'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(430171 6918532 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(436224 6900117 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Muurola'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(429547 7368068 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(437754 6905014 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(435247 6901937 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(434749 6903828 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tikkakoski'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(431528 6919344 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Rovaniemi'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(443094 7376769 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Rovaniemi'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(445054 7373325 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385834 6671834 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385769 6671648 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(390588 6674705 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(391188 6675963 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385733 6672387 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385917 6671685 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385739 6672174 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(386368 6671865 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385060 6671910 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(386128 6671884 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(386680 6671860 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385988 6672281 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385134 6671387 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(384651 6670863 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(384594 6669806 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(398943 6677478 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(383150 6679712 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(387781 6674061 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385061 6673142 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385549 6672063 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(382097 6674712 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(387775 6671870 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(393504 6676329 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(393544 6682047 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(382976 6680207 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(393457 6678416 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(386479 6674477 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385108 6672005 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(384737 6676266 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(387193 6676472 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(386636 6672506 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(386606 6671105 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(391845 6673461 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(389488 6673716 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(386368 6671865 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(388212 6682981 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385161 6671984 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(382746 6678922 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(386862 6677213 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(382453 6671145 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(384594 6670416 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385107 6675358 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(382389 6676208 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(382334 6675371 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(381902 6681044 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385661 6679306 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385761 6674354 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(384594 6670416 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(386644 6671112 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(388953 6680223 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(386262 6675662 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(393343 6678016 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(384294 6674198 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(383951 6674472 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(382293 6677240 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(384613 6670505 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(386630 6675024 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(382344 6670414 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385834 6671834 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(391295 6683656 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(392307 6676003 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(388811 6683777 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(384002 6674537 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385834 6671834 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385726 6673460 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Vantaa'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(391512 6687461 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(385603 6675442 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(390457 6678346 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(384875 6673716 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(384715 6673148 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(397179 6675825 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(389329 6681798 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Vantaa'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(387747 6685487 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Vantaa'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(395342 6683700 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(370272 6676538 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Vantaa'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(387378 6685718 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Vantaa'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(395241 6680270 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Helsinki'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(387269 6671385 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Vantaa'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(387555 6688342 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Vantaa'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(379610 6684543 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(370149 6676330 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Vantaa'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(387339 6686161 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(373947 6689422 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Vantaa'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(384651 6684299 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(372905 6680330 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(369855 6670472 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Vantaa'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(383481 6686203 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(376068 6684105 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(367032 6680438 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(375510 6677437 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(379576 6673884 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(375717 6672983 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(378744 6678005 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(378374 6676088 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(375697 6678292 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(379620 6673017 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(320535 6825402 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(375642 6673462 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(378208 6673123 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(328770 6822270 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(378359 6677929 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(327603 6822555 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(323354 6825818 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(374675 6671105 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Espoo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(372395 6671898 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(331951 6817382 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(333978 6821856 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(328385 6823983 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(327856 6822929 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(329025 6822582 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(330968 6822140 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(326694 6823153 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(336815 6824371 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(325599 6819107 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(240864 6711180 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(244545 6715527 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(329390 6820654 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(328581 6822909 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(323824 6823856 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(327158 6817649 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(327921 6822283 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(239758 6710960 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(241134 6712010 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(240135 6711198 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(237524 6709412 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(328300 6822580 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(320579 6823792 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(330270 6823106 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(241516 6711164 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(239021 6711383 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(239295 6710687 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(239883 6710647 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(327237 6821009 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(331967 6820120 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(329978 6822546 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(328101 6820149 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(240619 6717676 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(242817 6710553 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(240220 6709942 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(240933 6709955 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(245089 6720639 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(230557 6708740 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(322815 6825661 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Tampere'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(331686 6821798 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(242198 6713089 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(239880 6711097 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Varjakka'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(422489 7205885 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Joensuu'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(641926 6944483 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(235067 6711180 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(239536 6710444 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(240153 6710320 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(235397 6711902 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Oulu'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(429568 7203396 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Oulunsalo'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(423568 7200807 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Joensuu'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(642091 6944778 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Joensuu'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(640834 6943803 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(239730 6711532 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(238070 6710379 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(237010 6709445 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(240220 6714662 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Joensuu'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(639838 6945765 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Joensuu'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(635153 6948468 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(432339 6896050 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(432686 6900769 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(241404 6710820 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(237066 6714178 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(239284 6711685 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Turku'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(239204 6711191 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(433004 6901948 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(438082 6901555 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Joensuu'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(644118 6946384 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Jyväskylä'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(432980 6900421 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Joensuu'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(642830 6943352 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Joensuu'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(642531 6944070 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Oulu'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(427370 7215940 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Joensuu'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(643744 6942059 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Joensuu'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(644376 6945608 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');

      asset_id := nextval('PRIMARY_KEY_SEQ');
      service_point_value_id := nextval('PRIMARY_KEY_SEQ');
      insert into asset (id, asset_type_id, municipality_code, created_date, created_by, geometry) values (asset_id, 250, (select id from municipality where name_fi = 'Joensuu'),current_timestamp, 'service_point_import', ST_GeomFromText('POINT(641642 6943876 0 0)',3067));
      insert into service_point_value (id, asset_id, type, name, is_authority_data) values (service_point_value_id, asset_id, 10, 'Taksiasema', '0');
  END;
