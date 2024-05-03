ALTER TABLE road_link_replacement_work_list
ADD removed_geometry                   geometry,
ADD    added_geometry                  geometry,
ADD hausdorff_similarity_measure       NUMERIC(1000, 3),
ADD area_similarity_measure            NUMERIC(1000, 3),
ADD created_date                       timestamp