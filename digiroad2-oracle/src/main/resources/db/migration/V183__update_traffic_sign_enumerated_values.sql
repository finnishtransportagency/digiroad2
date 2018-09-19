-- Corrections at dropdown texts to Traffic signs
UPDATE
	ENUMERATED_VALUE
SET
	NAME_FI = 'Nopeusrajoitus päättyy',
	MODIFIED_DATE = SYSDATE,
	MODIFIED_BY = 'db_migration_v183'
WHERE
	id = (SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI = 'Nopeusrajoitus Päättyy');


UPDATE
	ENUMERATED_VALUE
SET
	NAME_FI = 'Taajama Päättyy',
	MODIFIED_DATE = SYSDATE,
	MODIFIED_BY = 'db_migration_v183'
WHERE
	id = (SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI = 'Taajama päättyy');


UPDATE
	ENUMERATED_VALUE
SET
	NAME_FI = 'Suurin Sallittu Pituus',
	MODIFIED_DATE = SYSDATE,
	MODIFIED_BY = 'db_migration_v183'
WHERE
	id = (SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI = 'Suurin sallittu pituus');


UPDATE
	ENUMERATED_VALUE
SET
	NAME_FI = 'Vasemmalle Kääntyminen Kielletty',
	MODIFIED_DATE = SYSDATE,
	MODIFIED_BY = 'db_migration_v183'
WHERE
	id = (SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI = 'Vasemmalle kääntyminen kielletty');


UPDATE
	ENUMERATED_VALUE
SET
	NAME_FI = 'Oikealle Kääntyminen Kielletty',
	MODIFIED_DATE = SYSDATE,
	MODIFIED_BY = 'db_migration_v183'
WHERE
	id = (SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI = 'Oikealle kääntyminen kielletty');


UPDATE
	ENUMERATED_VALUE
SET
	NAME_FI = 'U-Käännös Kielletty',
	MODIFIED_DATE = SYSDATE,
	MODIFIED_BY = 'db_migration_v183'
WHERE
	id = (SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI = 'U-käännös kielletty');


UPDATE
	ENUMERATED_VALUE
SET
	NAME_FI = 'Moottorikäyttöisellä ajoveuvolla ajo kielletty',
	MODIFIED_DATE = SYSDATE,
	MODIFIED_BY = 'db_migration_v183'
WHERE
	id = (SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI = 'Moottorikäyttöisellä ajoneuvolla ajo kielletty');