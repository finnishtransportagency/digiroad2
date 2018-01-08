CREATE OR REPLACE
PROCEDURE road_network_check
IS
  v_road_number        NUMBER;
  v_road_part_number   NUMBER;
  v_track_code         NUMBER;
  v_discontinuity      NUMBER;
  v_start_addr_m       NUMBER;
  v_end_addr_m         NUMBER;
  v_lrm_position_id    NUMBER;
  v_start_date         DATE;
  v_end_date           DATE;
  v_calibration_points NUMBER;
  v_ely                NUMBER;
  v_road_type          NUMBER;
  x1                   NUMBER;
  y1                   NUMBER;
  x2                   NUMBER;
  y2                   NUMBER;
  min_start_date       DATE;
  max_end_date         DATE;
  current_track        NUMBER := 0;
  --track_code_change number:=0;
FUNCTION Write_error
  RETURN BOOLEAN
IS
BEGIN
  -- Aqui inserir na tabela de erros
  RETURN TRUE;
END;

FUNCTION Check_tracks
  RETURN BOOLEAN
IS
  start_track1 NUMBER;
  start_track2 NUMBER;
  end_track1   NUMBER;
  end_track2   NUMBER;
BEGIN
  SELECT MIN(start_addr_m),
    end_addr_m
  INTO start_track1,
    end_track1
  FROM road_address
  WHERE road_number    = v_road_number
  AND road_part_number = v_road_part_number
  AND track_code       = 1;
  SELECT start_addr_m,
    end_addr_m
  INTO start_track2,
    end_track2
  FROM road_address
  WHERE road_number    = v_road_number
  AND road_part_number = v_road_part_number
  AND track_code       = 2;
  IF start_track1     != start_track2 OR end_track1 != end_track2 THEN
    -- Aqui fazer o erro
    RETURN FALSE;
  END IF;
  RETURN TRUE;
END;

BEGIN
  FOR r IN
  (SELECT DISTINCT road_number as r_number,
    road_part_number as r_p_number
  FROM road_address
  ORDER BY road_number,
    road_part_number
  )
  LOOP
    FOR road IN
    (SELECT ra.road_number as rn,
      ra.road_part_number as rpn,
      ra.track_code as tc,
      ra.discontinuity as ds,
      ra.start_addr_m as sam,
      ra.end_addr_m as eam,
      ra.lrm_position_id as lpi,
      ra.start_date as sd,
      ra.end_date as ed,
      ra.calibration_points as cp,
      ra.ely as el,
      ra.road_type as rtp,
      t.x,
      t.y,
      t2.x,
      t2.y,
      MIN(ra.start_date),
      MAX(ra.end_date)
    INTO v_road_number,
      v_road_part_number,
      v_track_code,
      v_discontinuity,
      v_start_addr_m,
      v_end_addr_m,
      v_lrm_position_id,
      v_start_date,
      v_end_date,
      v_calibration_points,
      v_ely,
      v_road_type,
      x1,
      y1,
      x2,
      y2,
      min_start_date,
      max_end_date
    FROM road_address ra,
      TABLE(sdo_util.Getvertices(geometry)) t,
      TABLE(sdo_util.Getvertices(geometry)) t2
    WHERE ra.road_number    = r.r_number
    AND ra.road_part_number = r.r_p_number
    AND ra.terminated       = 0
    AND t.id             = 1
    AND t2.id            = 2
    GROUP BY ra.road_number,
      ra.road_part_number,
      ra.track_code,
      ra.discontinuity,
      ra.start_addr_m,
      ra.end_addr_m,
      ra.lrm_position_id,
      ra.start_date,
      ra.end_date,
      ra.calibration_points,
      ra.ely,
      ra.road_type,
      t.x,
      t.y,
      t2.x,
      t2.y
    HAVING ra.start_date             >= MIN(ra.start_date)
    AND NVL(ra.end_date, '01.01.01') <= NVL(MAX(ra.end_date), '01.01.01')
    ORDER BY ra.track_code
    )
    LOOP
    DBMS_OUTPUT.PUT_LINE('Teste');
      --IF v_track_code != current_track THEN
      --current_track := v_track_code;

      --return;
      --END IF;
    END LOOP;
    --if track_code > 0 then
    -- compare start and end of the tracks (calibration points)
    --if v_start_addr_m = 0 then
    --start_track_address :=
    --end if;
  END LOOP;
END;