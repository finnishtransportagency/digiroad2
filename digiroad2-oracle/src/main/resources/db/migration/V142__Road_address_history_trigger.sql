CREATE OR REPLACE TRIGGER roadaddress_check
   BEFORE INSERT OR UPDATE /** https://stackoverflow.com/questions/3646110/difference-before-and-after-trigger-in-oracle**/
   ON Road_Address
   FOR EACH ROW
DECLARE
	roadAddress_history_exception EXCEPTION;
	PRAGMA EXCEPTION_INIT(roadAddress_history_exception, -20011);
	error_c NUMBER (1);
	BEGIN
	SELECT 1
	INTO error_c
    FROM road_address existing
	WHERE
      existing.id != :NEW.id AND
      ((existing.valid_to is null AND (:NEW.valid_to is null OR existing.valid_from < :NEW.valid_to)) OR
      (existing.valid_to is not null AND NOT (:NEW.valid_to <= existing.valid_from OR :NEW.valid_from >= existing.valid_to))) AND
      ((existing.END_DATE is null AND (:NEW.end_date is null OR existing.start_date < :NEW.end_date)) OR
      (existing.END_DATE is not null AND NOT (:NEW.END_DATE <= existing.START_DATE OR :NEW.START_DATE >= existing.END_DATE))) AND
      existing.ROAD_NUMBER = :NEW.ROAD_NUMBER AND
      existing.ROAD_PART_NUMBER = :NEW.ROAD_PART_NUMBER AND
      (existing.TRACK_CODE = :NEW.TRACK_CODE OR :NEW.TRACK_CODE = 0 OR existing.TRACK_CODE = 0) AND
      NOT (existing.END_ADDR_M <= :NEW.START_ADDR_M OR existing.START_ADDR_M >= :NEW.END_ADDR_M)
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
