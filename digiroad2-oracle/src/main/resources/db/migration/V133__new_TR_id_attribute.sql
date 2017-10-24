  ALTER TABLE PROJECT ADD TR_ID NUMBER;

/* In order to avoid having Sync errors when Issuing new Id's to The projects we need to bring up both sequences in sync in this point */
  DROP SEQUENCE VIITE_PROJECT_SEQ;
  declare
      lastSeq number;
  begin
      SELECT VIITE_GENERAL_SEQ.nextval INTO lastSeq FROM dual;
      if lastSeq IS NULL then lastSeq := 1; end if;
      execute immediate 'CREATE SEQUENCE VIITE_PROJECT_SEQ INCREMENT BY 1 START WITH ' || lastSeq || ' MAXVALUE 999999999 MINVALUE 1 NOCACHE';
  end;

  UPDATE PROJECT SET TR_ID = PROJECT.ID WHERE STATE IN (2,3,4,5);