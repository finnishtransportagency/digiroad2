CREATE TABLE road_link_replacement_work_list
(
    id                                 bigint NOT NULL,
    removed_link_id                    varchar(40),
    added_link_id                      varchar(40)
);

alter table road_link_replacement_work_list add primary key (id);
