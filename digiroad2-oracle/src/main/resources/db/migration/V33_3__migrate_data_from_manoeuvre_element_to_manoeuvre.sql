alter table manoeuvre
add (type number(1, 0) not null,
     modified_date timestamp default current_timestamp not null,
     modified_by varchar2(128),
     valid_to timestamp);

insert into manoeuvre(id, type, modified_date, modified_by, valid_to)
    select max(id), max(type), max(modified_date), max(modified_by), max(valid_to) from manoeuvre_element group by id;

alter table manoeuvre_element drop column type;
alter table manoeuvre_element drop column modified_date;
alter table manoeuvre_element drop column modified_by;
alter table manoeuvre_element drop column valid_to;
