CREATE OR REPLACE TRIGGER roadaddress_check
   BEFORE INSERT
   ON Road_Address
   FOR EACH ROW
DECLARE
	roadAddress_history_exception EXCEPTION;
	PRAGMA EXCEPTION_INIT(roadAddress_history_exception, -20011);
	error_c NUMBER (1);
	BEGIN
	SELECT COUNT (*)
	INTO error_c
    FROM road_address
	WHERE
	road_number=:NEW.road_number AND road_part_number=:NEW.road_part_number AND track_code=:NEW.track_code
	AND
	(/* check that addr M values overlaps with old ones*/
	(start_Addr_M between :NEW.start_Addr_M AND :NEW.end_Addr_M) OR (end_Addr_M between :NEW.start_Addr_M AND :NEW.end_Addr_M)
	OR
	(:NEW.start_Addr_M between start_Addr_M AND end_Addr_M) OR (:NEW.end_Addr_M between start_Addr_M AND end_Addr_M)
	)
	OR
	( /*check overlapping dates*/
	(:NEW.valid_from between valid_from AND valid_to) OR (:NEW.valid_to between valid_from AND valid_to)
	OR
	(:NEW.valid_to between valid_from AND valid_to) OR (:NEW.valid_to between valid_from AND valid_to)
	OR
	(valid_from between :NEW.valid_from AND :NEW.valid_to) OR (valid_to between :NEW.valid_from AND :NEW.valid_to)
	OR
	(valid_to between :NEW.valid_from AND :NEW.valid_to) OR (valid_to between :NEW.valid_from AND :NEW.valid_to)
	)
	OR
	(valid_from<:NEW.valid_from AND start_date>:NEW.start_date)   /*to prevent alteration to history * cant add older road_start date to road than there already is*/
	OR
	AND ROWNUM < 2;
   IF error_c > 0
   THEN
      RAISE roadAddress_history_exception;
   END IF;
EXCEPTION
   WHEN roadAddress_history_exception
   THEN
      raise_application_error (-20011, 'Road address history mapping failure');
END;