UPDATE ENUMERATED_VALUE SET NAME_FI = 'Linja-autopysäkki'
WHERE NAME_FI = 'Linja-autojen paikallisliikenne' AND PROPERTY_ID = (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'pysakin_tyyppi');

UPDATE ENUMERATED_VALUE SET NAME_FI = 'Kaukoliikenne (vanha tll)'
WHERE NAME_FI = 'Linja-autojen kaukoliikenne' AND PROPERTY_ID = (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'pysakin_tyyppi');

UPDATE ENUMERATED_VALUE SET NAME_FI = 'Pikavuoro (vanha tll)'
WHERE NAME_FI = 'Linja-autojen pikavuoro' AND PROPERTY_ID = (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'pysakin_tyyppi');