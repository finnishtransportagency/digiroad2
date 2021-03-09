create or replace FUNCTION to_2d (geom MDSYS.SDO_GEOMETRY)
      RETURN MDSYS.SDO_GEOMETRY DETERMINISTIC
   AS
      geom_2d       MDSYS.SDO_GEOMETRY;
      dim_count     INTEGER;       -- number of dimensions in layer
      gtype         INTEGER;       -- geometry type (single digit)
      n_points      INTEGER;       -- number of points in ordinates array
      n_ordinates   INTEGER;       -- number of ordinates
      i             INTEGER;
      j             INTEGER;
      k             INTEGER;
      offset        INTEGER;
   BEGIN
          -- If the input geometry is null, just return null
      IF geom IS NULL
      THEN
         RETURN (NULL);
      END IF;
       -- Get the number of dimensions from the gtype
      IF LENGTH (geom.sdo_gtype) = 4
      THEN
         dim_count := SUBSTR (geom.sdo_gtype, 1, 1);
         gtype := SUBSTR (geom.sdo_gtype, 4, 1);
      ELSE
              -- Indicate failure
         raise_application_error (-20000, 'Unable to determine dimensionality from gtype');
      END IF;
      IF dim_count = 2
      THEN
              -- Nothing to do, geometry is already 2D
         RETURN (geom);
      END IF;
       -- Construct and prepare the output geometry
      geom_2d :=
         MDSYS.SDO_GEOMETRY (2000 + gtype,
                             geom.sdo_srid,
                             geom.sdo_point,
                             MDSYS.sdo_elem_info_array (),
                             MDSYS.sdo_ordinate_array ()
                            );
       -- Process the point structure
      IF geom_2d.sdo_point IS NOT NULL
      THEN
         -- It's a point, that's it...
         geom_2d.sdo_point.z := NULL;
      ELSE
         -- It's not a point
         -- Process the ordinates array
         -- Prepare the size of the output array
         n_points := geom.sdo_ordinates.COUNT / dim_count;
         n_ordinates := n_points * 2;
         geom_2d.sdo_ordinates.EXTEND (n_ordinates);

           -- Copy the ordinates array
         j := geom.sdo_ordinates.FIRST;                        -- index into input elem_info array
         k := 1;                                               -- index into output ordinate array
         FOR i IN 1 .. n_points
         LOOP
            geom_2d.sdo_ordinates (k) := geom.sdo_ordinates (j);                         -- copy X
            geom_2d.sdo_ordinates (k + 1) := geom.sdo_ordinates (j + 1);                 -- copy Y
            j := j + dim_count;
            k := k + 2;
         END LOOP;
           -- Process the element info array
         -- Copy the input array into the output array
         geom_2d.sdo_elem_info := geom.sdo_elem_info;

           -- Adjust the offsets
         i := geom_2d.sdo_elem_info.FIRST;
         WHILE i < geom_2d.sdo_elem_info.LAST
         LOOP
            offset := geom_2d.sdo_elem_info (i);
            geom_2d.sdo_elem_info (i) := (offset - 1) / dim_count * 2 + 1;
            i := i + 3;
         END LOOP;
      END IF;
      RETURN geom_2d;
   END to_2d;