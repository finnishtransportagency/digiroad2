UPDATE ASSET SET VALID_TO = sysdate WHERE VALID_TO IS NULL AND ID IN
    (SELECT ASSET_ID FROM SINGLE_CHOICE_VALUE WHERE ENUMERATED_VALUE_ID IN
        (SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI IN
            ('I1 Sulkupuomi', 'I2.1 Sulkuaita', 'I2.2 Sulkuaita nuolilla', 'I3.1 Sulkupylväs vasemmalla', 'I3.2 Sulkupylväs oikealla',
             'I3.3 Sulkupylväs', 'I4 Sulkukartio', 'I12.1 Reunapaalu vasemmalla', 'I12.2 Reunapaalu oikealla')));

DELETE FROM SINGLE_CHOICE_VALUE WHERE ENUMERATED_VALUE_ID IN
    (SELECT ID FROM ENUMERATED_VALUE WHERE NAME_FI IN
        ('I1 Sulkupuomi', 'I2.1 Sulkuaita', 'I2.2 Sulkuaita nuolilla', 'I3.1 Sulkupylväs vasemmalla', 'I3.2 Sulkupylväs oikealla',
         'I3.3 Sulkupylväs', 'I4 Sulkukartio', 'I12.1 Reunapaalu vasemmalla', 'I12.2 Reunapaalu oikealla'));

DELETE FROM ENUMERATED_VALUE WHERE NAME_FI IN ('I1 Sulkupuomi', 'I2.1 Sulkuaita', 'I2.2 Sulkuaita nuolilla', 'I3.1 Sulkupylväs vasemmalla', 'I3.2 Sulkupylväs oikealla',
'I3.3 Sulkupylväs', 'I4 Sulkukartio', 'I12.1 Reunapaalu vasemmalla', 'I12.2 Reunapaalu oikealla');

UPDATE ENUMERATED_VALUE SET NAME_FI = 'C42 Taksin pysähtymispaikka' WHERE NAME_FI = 'C42 Taksin pysäyttämispaikka';
UPDATE ENUMERATED_VALUE SET NAME_FI = 'D2 Pakollinen ajosuunta' WHERE NAME_FI = 'D2 Pakollinen kiertosuunta';
UPDATE ENUMERATED_VALUE SET NAME_FI = 'H22.1 Etuajo-oikeutetun liikenteen suunta' WHERE NAME_FI = 'H22.1 Etuajooikeutetun liikenteen suunta';
UPDATE ENUMERATED_VALUE SET NAME_FI = 'H22.2 Etuajo-oikeutetun liikenteen suunta kääntyville' WHERE NAME_FI = 'H22.2 Etuajooikeutetun liikenteen suunta kääntyville';