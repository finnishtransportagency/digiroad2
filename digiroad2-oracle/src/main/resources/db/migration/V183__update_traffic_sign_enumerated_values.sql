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
	NAME_FI = 'Taajama päättyy',
	MODIFIED_DATE = SYSDATE,
	MODIFIED_BY = 'db_migration_v183'
WHERE
	id = (SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI = 'Taajama Päättyy');


UPDATE
	ENUMERATED_VALUE
SET
	NAME_FI = 'Suurin sallittu pituus',
	MODIFIED_DATE = SYSDATE,
	MODIFIED_BY = 'db_migration_v183'
WHERE
	id = (SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI = 'Suurin Sallittu Pituus');


UPDATE
	ENUMERATED_VALUE
SET
	NAME_FI = 'Vasemmalle kääntyminen kielletty',
	MODIFIED_DATE = SYSDATE,
	MODIFIED_BY = 'db_migration_v183'
WHERE
	id = (SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI = 'Vasemmalle Kääntyminen Kielletty');


UPDATE
	ENUMERATED_VALUE
SET
	NAME_FI = 'Oikealle kääntyminen kielletty',
	MODIFIED_DATE = SYSDATE,
	MODIFIED_BY = 'db_migration_v183'
WHERE
	id = (SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI = 'Oikealle Kääntyminen Kielletty');


UPDATE
	ENUMERATED_VALUE
SET
	NAME_FI = 'U-käännös kielletty',
	MODIFIED_DATE = SYSDATE,
	MODIFIED_BY = 'db_migration_v183'
WHERE
	id = (SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI = 'U-Käännös Kielletty');


UPDATE
	ENUMERATED_VALUE
SET
	NAME_FI = 'Moottorikäyttöisellä ajoneuvolla ajo kielletty',
	MODIFIED_DATE = SYSDATE,
	MODIFIED_BY = 'db_migration_v183'
WHERE
	id = (SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI = 'Moottorikäyttöisellä ajoveuvolla ajo kielletty');