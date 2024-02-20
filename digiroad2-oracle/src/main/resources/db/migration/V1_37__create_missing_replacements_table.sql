CREATE TABLE matched_road_links_work_list
(
    id                                 SERIAL PRIMARY KEY,
    removed_link_id                    varchar(40),
    added_link_id                      varchar(40),
    hausdorff_similarity_measure       numeric(1000, 3),
    area_similarity_measure            numeric(1000, 3)
);
