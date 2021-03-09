alter table asset drop constraint chk_mass_transits_municipality;

alter table asset add constraint chk_mass_transits_municipality
  check ((asset_type_id = 10 and municipality_code is not null) or (asset_type_id <> 10)) enable novalidate;
