ALTER TABLE manouvre_samuutus_work_list ADD COLUMN exception_types INTEGER[];
ALTER TABLE manouvre_samuutus_work_list ADD COLUMN validity_periods TEXT[];
ALTER TABLE manouvre_samuutus_work_list ADD COLUMN additional_info VARCHAR(4000);
ALTER TABLE manouvre_samuutus_work_list ADD COLUMN created_date timestamp;
