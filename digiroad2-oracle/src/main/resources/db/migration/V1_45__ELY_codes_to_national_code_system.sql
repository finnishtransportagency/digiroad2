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
