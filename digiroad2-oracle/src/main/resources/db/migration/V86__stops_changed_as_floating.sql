
--Insert two new not visible fields at form MassTransitSop
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID) VALUES
                                            (primary_key_seq.nextval, 10, 'text', 0, 'db_migration_v87', 'linkin_hallinnollinen_luokka');
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID) VALUES
                                            (primary_key_seq.nextval, 10, 'text', 0, 'db_migration_v87', 'kellumisen_syy');
