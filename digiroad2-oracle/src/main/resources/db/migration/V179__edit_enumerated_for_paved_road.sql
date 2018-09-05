delete from ENUMERATED_VALUE where name_fi =  'Päällystemätön tie';

update enumerated_value set name_fi = 'Päällystetty, tyyppi tuntematon' where name_fi =  'Päällysteen tyyppi tuntematon';

--Create single choice value for oldest paved road asset
INSERT INTO SINGLE_CHOICE_VALUE
(ASSET_ID, ENUMERATED_VALUE_ID, PROPERTY_ID, MODIFIED_DATE, MODIFIED_BY)
SELECT
	a.ID AS ASSET_ID,
	(SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI = 'Päällystetty, tyyppi tuntematon') AS ENUMERATED_VALUE_ID,
	(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'paallysteluokka' AND ASSET_TYPE_ID = 110) AS PROPERTY_ID,
	SYSDATE AS MODIFIED_DATE,
	'db_migration_v179' AS MODIFIED_BY
FROM
	ASSET A,
	NUMBER_PROPERTY_VALUE NPV
WHERE
	A.ASSET_TYPE_ID = 110
	AND A.VALID_TO IS NULL
	AND A.id = npv.ASSET_ID;