
create table vallu_xml_ids (
  id number primary key,
  asset_id number references asset not null,
  created_date DATE default sysdate,
 constraint assetId_createDate UNIQUE (asset_id, created_date)
);
