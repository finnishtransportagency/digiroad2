CREATE OR REPLACE TRIGGER road_address_changes_no_overlap
  BEFORE
    INSERT
  ON road_address_changes
  FOR EACH ROW
DECLARE
  ExistingId NUMBER;
  CURSOR Overlap_cursor (proj_id NUMBER, road_number NUMBER, road_part_number NUMBER, start_m NUMBER, end_m NUMBER, track_code NUMBER) IS
    SELECT TO_CHAR(NEW_ROAD_NUMBER) || '/' || TO_CHAR(NEW_ROAD_PART_NUMBER) FROM ROAD_ADDRESS_CHANGES
    WHERE project_id = proj_id AND (new_track_code = 0 OR track_code = 0 OR new_track_code = track_code) AND
      (start_m < NEW_END_ADDR_M AND end_m > NEW_START_ADDR_M);
BEGIN
  OPEN Project_cursor (:NEW.lrm_position_id, :NEW.project_id);
  FETCH Project_cursor INTO ExistingId;
  IF Project_cursor%NOTFOUND THEN
    NULL;
  ELSE
    Raise_application_error(-20000, 'Road address overlap for ' || TO_CHAR(ExistingId));
  END IF;
END;
