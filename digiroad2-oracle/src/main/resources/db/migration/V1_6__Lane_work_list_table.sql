CREATE TABLE lane_work_list
(
    id              bigint NOT NULL,
    link_id         numeric(38),
    property        varchar(128),
    old_value       integer,
    new_value       integer,
    created_date    timestamp,
    created_by      varchar(128)
);

alter table lane_work_list add primary key (id);