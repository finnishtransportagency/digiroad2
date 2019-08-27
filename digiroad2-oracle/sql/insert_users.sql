insert into service_user (id, username, configuration, name)
values (1, 'mun766', '{"authorizedMunicipalities":[766],"authorizedAreas":[],"roles":[]}', 'Single Municipality Maintainer 766');
insert into service_user (id, username, configuration, name)
values (2, 'multi_mun766,749', '{"authorizedMunicipalities":[766,749],"authorizedAreas":[],"roles":[]}', 'Multiple Municipality Maintainer 766,749');
insert into service_user (id, username, configuration, name)
values (3, 'ely1', '{"authorizedMunicipalities":[683,614,698,320,751,261,583,742,148,732,498,890,758,240,851,241,845,976,854,273,47],"authorizedAreas":[],"roles":["busStopMaintainer"]}', 'Single Ely Maintainer 1');
insert into service_user (id, username, configuration, name)
values (4, 'multi_ely1,2', '{"authorizedMunicipalities":[69,777,977,683,614,698,320,436,785,751,261,678,746,630,625,583,889,742,578,697,317,615,9,105,244,205,765,425,148,732,71,498,208,890,494,758,535,483,240,626,563,851,241,791,72,859,845,976,748,139,290,832,620,854,273,564,305,47,691],"authorizedAreas":[],"roles":["busStopMaintainer"]}', 'Multiple Ely Maintainer 1,2');
insert into service_user (id, username, configuration, name)
values (5, 'service9', '{"authorizedMunicipalities":[481,202,747,538,838,853,284,480,734,529,761,102,561,484,783,738,503,430,423,577,413,445,181,704,230,271,631,609,895,636,608,531,680,50,99,319,304,684,214,833,51,19,918,79,400,886,442,322],"authorizedAreas":[9],"roles":["serviceRoadMaintainer"]}', 'Single Service Maintainer 9');
insert into service_user (id, username, configuration, name)
values (6, 'multi_service3,5', '{"authorizedMunicipalities":[481,202,747,538,838,853,284,480,734,529,761,102,561,484,783,738,503,430,423,577,413,445,181,704,230,271,631,609,895,636,608,531,680,50,99,319,304,684,214,833,51,19,918,79,400,886,442,322],"authorizedAreas":[3,5],"roles":["serviceRoadMaintainer"]}', 'Multiple Service Maintainer 3,5');
insert into service_user (id, username, configuration, name)
values (7, 'silari', '{"authorizedMunicipalities":[],"authorizedAreas":[],"roles":["operator"]}', 'Operator');
insert into service_user (id, username, configuration, name)
values (8, 'ely7_service7', '{"authorizedMunicipalities":[481,202,747,538,838,853,284,480,734,529,761,102,561,484,783,738,503,430,423,577,413,445,181,704,230,271,631,609,895,636,608,531,680,50,99,319,304,684,214,833,51,19,918,79,400,886,442,322],"authorizedAreas":[7],"roles":["busStopMaintainer","serviceRoadMaintainer"]}', 'Single Ely Maintainer 7 And Single Service Maintainer 7');
insert into service_user (id, username, configuration, name)
values (9, 'ely1_multi_service1,2', '{"authorizedMunicipalities":[683,614,698,320,751,261,583,742,148,732,498,890,758,240,851,241,845,976,854,273,47],"authorizedAreas":[1,2],"roles":["busStopMaintainer","serviceRoadMaintainer"]}', 'Single Ely Maintainer 1 And Multiple Service Maintainer 1,2');
insert into service_user (id, username, configuration, name)
values (10, 'multi_ely1,2_service4', '{"authorizedMunicipalities":[69,777,977,683,614,698,320,436,785,751,261,678,746,630,625,583,889,742,578,697,317,615,9,105,244,205,765,425,148,732,71,498,208,890,494,758,535,483,240,626,563,851,241,791,72,859,845,976,748,139,290,832,620,854,273,564,305,47,691],"authorizedAreas":[4],"roles":["busStopMaintainer","serviceRoadMaintainer"]}', 'Multiple Ely Maintainer 1,2 And Single Service Maintainer 4');
insert into service_user (id, username, configuration, name)
values (11, 'multi_ely3,4_multi_service2,9', '{"authorizedMunicipalities":[892,846,249,893,408,217,5,440,10,500,288,301,52,934,421,152,216,475,924,164,179,905,280,265,849,312,74,233,598,275,729,435,403,499,989,256,77,601,584,743,291,545,236,172,599,399,226,145,182,231,931,495,759,218,946,272,287,151,300,410,992,850,592,232],"authorizedAreas":[2,9],"roles":["busStopMaintainer","serviceRoadMaintainer"]}', 'Multiple Ely Maintainer 3,4 And Multiple Service Maintainer 2,9');