select a.id as asset_id, p.id as property_id, p.name_fi as property_name, p.property_type, i.id as image_id, i.modified_date as image_modified_date,
  case
    when e.value is not null then e.value
    else null
  end as value,
  case
    when e.name_fi is not null then e.name_fi
    when tp.value_fi is not null then tp.value_fi
    else null
  end as display_value,
lrm.id, lrm.start_measure, lrm.end_measure, lrm.road_link_id,
SDO_CS.TRANSFORM(SDO_LRS.LOCATE_PT(rl.geom, lrm.start_measure), 3067) AS position
from asset_type t
join property p on t.id = p.asset_type_id
  join asset a on a.asset_type_id = t.id
    join lrm_position lrm on a.lrm_position_id = lrm.id
      join road_link rl on lrm.road_link_id = rl.id
    left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
    left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and p.property_type = 'text'
    left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'multiple_choice'
    left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
    left join image i on e.image_id = i.id
order by a.id;