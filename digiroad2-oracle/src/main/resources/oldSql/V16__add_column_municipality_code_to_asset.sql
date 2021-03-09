alter table asset
  add (municipality_code number);

create index municipality_code_idx
  on asset ("MUNICIPALITY_CODE");
