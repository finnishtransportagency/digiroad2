alter table asset
  add floating char(1) default '0'
  add constraint cons_floating_is_boolean check (floating in ('1','0'));

create index asset_floating_idx
  on asset ("FLOATING");
