-- delete all but the most recent duplicate value from text property value
delete from text_property_value
where id in (
 select id from (
   select id, asset_id, property_id, count(id) over (partition by asset_id, property_id) cnt
   from text_property_value)
 where cnt > 1
)
and id not in (
 SELECT max(id)
 FROM text_property_value
 GROUP BY asset_id, property_id
 having count(property_id) > 1
);

-- drop existing (non-unique) index on asset_id and property_id
drop index text_property_sx;

-- create unique index on asset_id and property_id so that duplicates cannot be created anymore
CREATE UNIQUE INDEX AID_PID_TEXT_PROPERTY_SX ON TEXT_PROPERTY_VALUE ("ASSET_ID", "PROPERTY_ID");