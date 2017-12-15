CREATE OR REPLACE TRIGGER road_address_history_check BEFORE
  UPDATE OR
  INSERT ON ROAD_ADDRESS FOR EACH ROW DECLARE v_duplicated NUMBER (2);
  BEGIN
    ELSE
      SELECT COUNT (*)
      INTO v_duplicated
      FROM ROAD_ADDRESS
      WHERE road_number                  = :NEW.ROAD_NUMBER
      AND road_part_number               = :NEW.ROAD_PART_NUMBER
      AND START_ADDR_M                   = :NEW.START_ADDR_M
      AND END_ADDR_M                     = :NEW.END_ADDR_M
      AND TRACK_CODE                     = :NEW.TRACK_CODE
      AND DISCONTINUITY                  = :NEW.DISCONTINUITY
      AND to_date(START_DATE,'RR.MM.DD') = to_date(:NEW.START_DATE,'RR.MM.DD')
      AND (to_date(END_DATE,'RR.MM.DD')  = to_date(:NEW.END_DATE,'RR.MM.DD')
      OR END_DATE                       IS NULL
      AND :NEW.END_DATE                 IS NULL)
      AND to_date(VALID_FROM,'RR.MM.DD') = to_date(:NEW.VALID_FROM,'RR.MM.DD')
      AND (to_date(VALID_TO,'RR.MM.DD')  = to_date(:NEW.VALID_TO,'RR.MM.DD')
      OR VALID_TO                       IS NULL
      AND :NEW.VALID_TO                 IS NULL)
      AND ELY                            = :NEW.ELY
      AND ROAD_TYPE                      = :NEW.ROAD_TYPE
      AND TERMINATED                     = :NEW.TERMINATED;
      IF v_duplicated                    > 0 THEN
        RAISE_APPLICATION_ERROR(-20002,'Atempted to insert a duplicate road address (road_number:' || :NEW.ROAD_NUMBER || ', road_part:' || :NEW.ROAD_PART_NUMBER || ', start_addr_m:' || :NEW.START_ADDR_M || ', end_addr_m:' || :NEW.END_ADDR_M ||')');
      END IF;
    END IF;
  END;
  /