-- New AdditionalInfo Field

ALTER TABLE PROHIBITION_VALUE
    ADD (
          ADDITIONAL_INFO  VARCHAR2(4000 BYTE)
        );