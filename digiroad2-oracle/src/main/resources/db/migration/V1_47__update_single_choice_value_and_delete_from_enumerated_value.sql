-- Päivitettävät asset-taulun rivit

CREATE TEMPORARY TABLE updated_asset_ids (
    asset_id INT
);

-- Asfaltti

CREATE TEMPORARY TABLE hard_and_soft_asphalt_id AS 
SELECT ev.id 
FROM enumerated_value ev 
JOIN property p ON ev.property_id = p.id
AND public_id = 'paallysteluokka' AND 
ev.name_fi IN ('Kovat asfalttibetonit', 'Pehmeät asfalttibetonit');

WITH updated_rows AS (
    UPDATE single_choice_value  
    SET enumerated_value_id = (	
       SELECT ev.id 
       FROM enumerated_value ev 
       WHERE ev.name_fi = 'Asfaltti')
    WHERE enumerated_value_id IN (SELECT id FROM hard_and_soft_asphalt_id)
    RETURNING asset_id
)
INSERT INTO updated_asset_ids (asset_id)
SELECT asset_id FROM updated_rows;

DELETE FROM enumerated_value ev
WHERE ev.id IN (SELECT id FROM hard_and_soft_asphalt_id);

DROP TABLE IF EXISTS hard_and_soft_asphalt_id;

-- Sitomaton kulutuskerros

CREATE TEMPORARY TABLE gravel_surface_and_gravel_wear_layer_id AS 
SELECT ev.id 
FROM enumerated_value ev 
JOIN property p ON ev.property_id = p.id
AND public_id = 'paallysteluokka' AND 
ev.name_fi IN ('Sorakulutuskerros');

WITH updated_rows AS (
    UPDATE single_choice_value  
    SET enumerated_value_id = (
       SELECT ev.id 
       FROM enumerated_value ev 
       WHERE ev.name_fi = 'Sitomaton kulutuskerros')
    WHERE enumerated_value_id IN (SELECT id FROM gravel_surface_and_gravel_wear_layer_id)
    RETURNING asset_id
)
INSERT INTO updated_asset_ids (asset_id)
SELECT asset_id FROM updated_rows;

DELETE FROM enumerated_value ev
WHERE ev.id IN (SELECT id FROM gravel_surface_and_gravel_wear_layer_id);

DROP TABLE IF EXISTS gravel_surface_and_gravel_wear_layer_id;

-- Muut päällysteluokat

CREATE TEMPORARY TABLE concrete_and_other_coatings_id AS 
SELECT ev.id 
FROM enumerated_value ev 
JOIN property p ON ev.property_id = p.id
AND public_id = 'paallysteluokka' AND 
ev.name_fi IN ('Soratien pintaus', 'Betoni', 'Muut pinnoitteet');

WITH updated_rows AS (
    UPDATE single_choice_value  
    SET enumerated_value_id = (
       SELECT ev.id 
       FROM enumerated_value ev 
       WHERE ev.name_fi = 'Muut päällysteluokat')
    WHERE enumerated_value_id IN (SELECT id FROM concrete_and_other_coatings_id)
    RETURNING asset_id
)
INSERT INTO updated_asset_ids (asset_id)
SELECT asset_id FROM updated_rows;

DELETE FROM enumerated_value ev
WHERE ev.id IN (SELECT id FROM concrete_and_other_coatings_id);

DROP TABLE IF EXISTS concrete_and_other_coatings_id;


UPDATE asset a
SET modified_date = current_timestamp,
	modified_by = 'DROTH-4154'
WHERE a.id IN ( SELECT asset_id FROM updated_asset_ids);

DROP TABLE IF EXISTS updated_asset_ids;