  alter table service_point_value
    add is_authority_data char(1)
    add constraint is_authority_data_boolean check (is_authority_data in ('1','0'));
