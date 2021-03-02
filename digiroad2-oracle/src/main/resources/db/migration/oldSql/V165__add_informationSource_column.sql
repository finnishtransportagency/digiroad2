ALTER TABLE asset ADD information_source int
ADD CONSTRAINT information_source CHECK ( information_source in (1,2,3)) ;