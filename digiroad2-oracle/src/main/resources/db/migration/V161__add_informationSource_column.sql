ALTER TABLE asset ADD informationSource int
ADD CONSTRAINT informationSource CHECK ( informationSource in (1,2,3)) ;