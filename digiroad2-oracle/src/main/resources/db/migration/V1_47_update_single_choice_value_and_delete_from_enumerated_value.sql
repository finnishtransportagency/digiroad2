-- Asfaltti

CREATE TEMPORARY TABLE hard_and_soft_asphalt_id AS 
SELECT ev.id 
FROM enumerated_value ev 
WHERE ev.name_fi IN ('Kovat asfalttibetonit', 'Pehmeät asfalttibetonit');

UPDATE single_choice_value  
SET enumerated_value_id = (	
	SELECT ev.id 
	FROM enumerated_value ev 
	WHERE ev.name_fi = 'Asfaltti')
WHERE enumerated_value_id IN (SELECT id FROM hard_and_soft_asphalt_id);

DELETE FROM enumerated_value ev
WHERE ev.id IN (SELECT id FROM hard_and_soft_asphalt_id);

DROP TABLE IF EXISTS temp_asphalt_ids;

-- Sitomaton kulutuskerros

CREATE TEMPORARY TABLE gravel_surface_and_gravel_wear_layer_id AS 
SELECT ev.id 
FROM enumerated_value ev 
WHERE ev.name_fi IN ('Soratien pintaus', 'Sorakulutuskerros');

UPDATE single_choice_value  
SET enumerated_value_id = (
	SELECT ev.id 
	FROM enumerated_value ev 
	WHERE ev.name_fi = 'Sitomaton kulutuskerros')
WHERE enumerated_value_id IN (SELECT id FROM gravel_surface_and_gravel_wear_layer_id);

DELETE FROM enumerated_value ev
WHERE ev.id IN (SELECT id FROM gravel_surface_and_gravel_wear_layer_id)

-- Muut päällysteluokat

CREATE TEMPORARY TABLE concrete_and_other_coatings_id AS 
SELECT ev.id 
FROM enumerated_value ev 
WHERE ev.name_fi IN ('Betoni', 'Muut pinnoitteet');

UPDATE single_choice_value  
SET enumerated_value_id = (
	SELECT ev.id 
	FROM enumerated_value ev 
	WHERE ev.name_fi = 'Muut päällysteluokat')
WHERE enumerated_value_id IN (SELECT id FROM concrete_and_other_coatings_id);

DELETE FROM enumerated_value ev
WHERE ev.id IN (SELECT id FROM concrete_and_other_coatings_id);