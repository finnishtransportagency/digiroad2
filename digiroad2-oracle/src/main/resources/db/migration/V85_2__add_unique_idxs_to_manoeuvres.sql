CREATE UNIQUE INDEX uniq_first_element
  ON MANOEUVRE_ELEMENT(case when element_type=1 then MANOEUVRE_ID else null end);

CREATE UNIQUE INDEX uniq_last_element
  ON MANOEUVRE_ELEMENT(case when element_type=3 then MANOEUVRE_ID else null end);