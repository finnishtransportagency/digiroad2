alter table road_address
  add floating char(1) default '0'
  add constraint ra_floating_is_boolean check (floating in ('1','0'));

create index road_address_floating_idx
  on road_address ("FLOATING");