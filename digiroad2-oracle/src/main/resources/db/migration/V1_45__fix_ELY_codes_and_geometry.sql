UPDATE ely
SET id = id + 100;

UPDATE ely
SET id = CASE
    WHEN name_fi = 'Ahvenanmaa' THEN 16
    WHEN name_fi = 'Lappi' THEN 15
    WHEN name_fi = 'Pohjois-Pohjanmaa' THEN 13
    WHEN name_fi = 'Etela-Pohjanmaa' THEN 11
    WHEN name_fi = 'Keski-Suomi' THEN 10
    WHEN name_fi = 'Pohjois-Savo' THEN 8
    WHEN name_fi = 'Pirkanmaa' THEN 5
    WHEN name_fi = 'Kaakkois-Suomi' THEN 6
    WHEN name_fi = 'Uusimaa' THEN 1
    WHEN name_fi = 'Varsinais-Suomi' THEN 2
    ELSE id
END;

UPDATE municipality
SET ely_nro = CASE
    WHEN ely_nro = 0 THEN 16
    WHEN ely_nro = 1 THEN 15
    WHEN ely_nro = 2 THEN 13
    WHEN ely_nro = 3 THEN 11
    WHEN ely_nro = 4 THEN 10
    WHEN ely_nro = 5 THEN 8
    WHEN ely_nro = 6 THEN 5
    WHEN ely_nro = 7 THEN 6
    WHEN ely_nro = 8 THEN 1
    WHEN ely_nro = 9 THEN 2
    ELSE ely_nro
END;

UPDATE ely
SET geometry = ST_GeomFromText('POINT ZM(533880.796720066 6756886.74130609 0 0)', 3067)
WHERE id = 6;

UPDATE ely
SET geometry = ST_GeomFromText('POINT ZM(383467.095615344 6718181.18728251 0 0)', 3067)
WHERE id = 1;

UPDATE ely
SET geometry = ST_GeomFromText('POINT ZM(230120.084611288 6748148.87306083 0 0)', 3067)
WHERE id = 2;
