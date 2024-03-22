CREATE TABLE automatically_processed_lanes_work_list
(
    id              bigint NOT NULL,
    link_id         varchar(40),
    property        varchar(128),
    old_value       integer,
    new_value       integer,
    start_dates     varchar(128),
    created_date    timestamp,
    created_by      varchar(128)
);

alter table automatically_processed_lanes_work_list add primary key (id);