CREATE or replace TRIGGER project_link_reserved
  BEFORE
    INSERT
  ON project_link
  FOR EACH ROW
DECLARE
  ExistingId NUMBER;
  CURSOR Project_cursor (lrm_id NUMBER, proj_id NUMBER) IS
    SELECT p.id FROM PROJECT p JOIN PROJECT_LINK pl on (p.id = pl.project_id) JOIN LRM_POSITION l on (l.id = pl.LRM_POSITION_ID)
    WHERE p.id != proj_id AND l.link_id = (SELECT link_id FROM LRM_POSITION WHERE id = lrm_id) AND p.state in (0, 1);
BEGIN
  OPEN Project_cursor (:NEW.lrm_position_id, :NEW.project_id);
  FETCH Project_cursor INTO ExistingId;
  IF Project_cursor%NOTFOUND THEN
    NULL;
  ELSE
    Raise_application_error(-20000, 'link_id reserved to project id ' || TO_CHAR(ExistingId));
  END IF;
END project_link_reserved;
/

CREATE OR REPLACE TRIGGER road_address_chk_overlap
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
  OPEN Overlap_cursor (:NEW.project_id, :NEW.old_road_number, :NEW.old_road_part_number, :NEW.old_start_addr_m, :NEW.old_end_addr_m, :NEW.old_track_code);
  FETCH Overlap_cursor INTO ExistingId;
  IF Overlap_cursor%NOTFOUND THEN
    NULL;
  ELSE
    Raise_application_error(-20000, 'Road address overlap for ' || TO_CHAR(ExistingId));
  END IF;
END road_address_chk_overlap;