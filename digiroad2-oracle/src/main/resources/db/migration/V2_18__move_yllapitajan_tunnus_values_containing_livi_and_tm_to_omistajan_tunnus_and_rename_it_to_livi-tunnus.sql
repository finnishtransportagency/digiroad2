-- First delete existing values from "Omistajan tunnus" that will be replaced.
-- NB: These are mostly null values.

DELETE FROM text_property_value
WHERE property_id = (SELECT p.id FROM property p, localized_string ls
                   WHERE ls.id = p.name_localized_string_id
                   AND ls.value_fi = 'Omistajan tunnus')
AND asset_id IN (
  SELECT asset_id FROM text_property_value
  WHERE property_id = (SELECT p.id FROM property p, localized_string ls
                     WHERE ls.id = p.name_localized_string_id
                     AND ls.value_fi = 'Ylläpitäjän tunnus')
  AND (lower(value_fi) LIKE 'livi%' OR lower(value_fi) LIKE 'tm%')
);

-- Then change property ids of matching values from "Ylläpitäjän tunnus" to "Omistajan tunnus"

UPDATE text_property_value
SET property_id = (SELECT p.id FROM property p, localized_string ls
                   WHERE ls.id = p.name_localized_string_id
                   AND ls.value_fi = 'Omistajan tunnus'),
    modified_by = 'db_migration_v2.18',
    modified_date = sysdate
WHERE property_id = (SELECT p.id FROM property p, localized_string ls
                     WHERE ls.id = p.name_localized_string_id
                     AND ls.value_fi = 'Ylläpitäjän tunnus')
AND (lower(value_fi) LIKE 'livi%' OR lower(value_fi) LIKE 'tm%');

-- Finally, rename "Omistajan tunnus" to "LiVi-tunnus"

UPDATE localized_string
SET value_fi='LiVi-tunnus',
    modified_by='db_migration_v2.18',
    modified_date=sysdate
WHERE value_fi='Omistajan tunnus';
