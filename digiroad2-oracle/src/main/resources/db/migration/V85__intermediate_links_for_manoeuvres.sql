alter table manoeuvre_element add dest_link_id number(38, 0);

UPDATE MANOEUVRE_ELEMENT SET dest_link_id = COALESCE(
  (SELECT link_id FROM MANOEUVRE_ELEMENT me2 WHERE me2.manoeuvre_id = MANOEUVRE_ELEMENT.manoeuvre_id AND element_type=2),
  (SELECT link_id FROM MANOEUVRE_ELEMENT me2 WHERE me2.manoeuvre_id = MANOEUVRE_ELEMENT.manoeuvre_id AND element_type=3))
   WHERE element_type=1;

UPDATE MANOEUVRE_ELEMENT SET dest_link_id =
  (SELECT link_id FROM MANOEUVRE_ELEMENT me2 WHERE me2.manoeuvre_id = MANOEUVRE_ELEMENT.manoeuvre_id AND element_type=3)
   WHERE element_type=2;

alter table
   MANOEUVRE_ELEMENT
add constraint
   non_final_has_destination
   CHECK (element_type = 3 OR dest_link_id IS NOT NULL);

