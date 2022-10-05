CREATE TABLE municipality_email
(
    id                  bigint    PRIMARY KEY NOT NULL,
    email               varchar(255)    NOT NULL,
    municipality_code   bigint    NOT NULL,
    created_date        timestamp NOT NULL DEFAULT current_timestamp
);