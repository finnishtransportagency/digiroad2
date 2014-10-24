update asset a1
  set geometry = (select SDO_LRS.LOCATE_PT(rl.geom, LEAST(lrm.start_measure, SDO_LRS.GEOM_SEGMENT_END_MEASURE(rl.geom))) from asset a2
                          join asset_link al on al.asset_id = a2.id
                          join lrm_position lrm on lrm.id = al.position_id
                          join road_link rl on rl.id = lrm.road_link_id
                          where a1.id = a2.id)
  where a1.asset_type_id = 10;

