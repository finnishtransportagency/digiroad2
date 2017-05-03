UPDATE ENUMERATED_VALUE
SET VALUE = 99
WHERE NAME_FI = 'Tuntematon'
AND VALUE = 5
AND PROPERTY_ID = (select id from property where public_ID = 'huoltotie_kayttooikeus');

UPDATE ENUMERATED_VALUE
SET VALUE = 99
WHERE NAME_FI = 'Ei tietoa'
AND VALUE = 0
AND PROPERTY_ID = (select id from property where public_ID = 'huoltotie_huoltovastuu');