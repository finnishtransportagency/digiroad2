  ALTER TABLE PROJECT ADD TR_ID NUMBER;

/* In order to avoid having Sync errors when Issuing new Id's to The projects we need to bring up both sequences in sync in this point */
 DECLARE
    l_num INTEGER;
    generalCurrVal INTEGER;
  BEGIN
  SELECT last_Number into generalCurrVal FROM all_Sequences Where sequence_name = 'VIITE_GENERAL_SEQ';
    FOR i IN 1 .. (generalCurrVal-1)
    LOOP
      EXECUTE IMMEDIATE
        'Select VIITE_PROJECT_SEQ.NEXTVAL FROM DUAL'
      INTO l_num;
    END LOOP;
  END;

  UPDATE PROJECT SET TR_ID = VIITE_PROJECT_SEQ.NEXTVAL WHERE STATE = 3;
  UPDATE PROJECT SET TR_ID = PROJECT.ID WHERE STATE <> 3;