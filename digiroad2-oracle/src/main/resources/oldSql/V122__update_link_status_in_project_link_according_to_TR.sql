UPDATE PROJECT_LINK SET STATUS = CASE STATUS WHEN 1 THEN 5
                                             WHEN 2 THEN 2
                                             WHEN 3 THEN 3
                                             WHEN 4 THEN 1
                                             WHEN 5 THEN 4
                                             WHEN 0 THEN 0
                                             ELSE 99
                                             END;