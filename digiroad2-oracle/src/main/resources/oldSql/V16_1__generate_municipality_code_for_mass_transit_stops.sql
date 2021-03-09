update asset a1
  set a1.municipality_code =
    (select rl.kunta_nro from asset a2
      join asset_link al on al.asset_id = a2.id
      join lrm_position lrm on lrm.id = al.position_id
      join test_road_link_conv rl on lrm.road_link_id = rl.objectid
      where a1.id = a2.id)
  where a1.asset_type_id = 10;

