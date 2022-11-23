ALTER TABLE additional_panel DROP CONSTRAINT sys_c003961016;
ALTER TABLE additional_panel ADD CONSTRAINT sys_c003961016 CHECK (form_position <= 5);

ALTER TABLE additional_panel_history DROP CONSTRAINT sys_c003961116;
ALTER TABLE additional_panel_history ADD CONSTRAINT sys_c003961116 CHECK (form_position <= 5);
