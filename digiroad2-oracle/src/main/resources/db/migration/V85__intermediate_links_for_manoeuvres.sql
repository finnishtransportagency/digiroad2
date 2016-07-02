alter table manoeuvre_element add dest_link_id number(38, 0);

UPDATE MANOEUVRE_ELEMENT SET dest_link_id = (SELECT link_id FROM MANOEUVRE_ELEMENT me2 WHERE me2.manoeuvre_id = MANOEUVRE_ELEMENT.manoeuvre_id AND element_type=3) WHERE element_type=1;



