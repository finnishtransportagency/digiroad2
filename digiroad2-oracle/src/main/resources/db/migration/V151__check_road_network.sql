CREATE OR REPLACE PROCEDURE road_network_check IS

    min_start_date             DATE;
    max_end_date               DATE;
    current_road               NUMBER := 0;
    current_road_part          NUMBER := 0;
    current_track              NUMBER := 0;
    current_end_x              NUMBER := 0;
    current_end_y              NUMBER := 0;
    current_end_addr_m         NUMBER := 0;
    error_overlaping_address   NUMBER := 1;
    error_topology             NUMBER := 2;
    num_errors                 NUMBER := 0;

    FUNCTION write_error (
        road_address_id   IN NUMBER,
        error_code        IN NUMBER
    ) RETURN NUMBER
        IS
    BEGIN
        INSERT INTO road_network_errors VALUES (
            road_network_errors_key_seq.NEXTVAL,
            road_address_id,
            error_code
        );

        RETURN 1;
    END;

    FUNCTION check_tracks RETURN BOOLEAN IS
        start_track1   NUMBER;
        start_track2   NUMBER;
        end_track1     NUMBER;
        end_track2     NUMBER;
    BEGIN
        dbms_output.put_line('Checking tracks for road '
        || current_road
        || ' road part '
        || current_road_part);
        SELECT
            MIN(start_addr_m),
            MAX(end_addr_m)
        INTO
            start_track1,end_track1
        FROM
            road_address
        WHERE
            road_number = current_road
            AND   road_part_number = current_road_part
            AND   track_code = 1;

        SELECT
            MIN(start_addr_m),
            MAX(end_addr_m)
        INTO
            start_track2,end_track2
        FROM
            road_address
        WHERE
            road_number = current_road
            AND   road_part_number = current_road_part
            AND   track_code = 2;

        IF
            start_track1 != start_track2 OR end_track1 != end_track2
        THEN
            RETURN false;
        END IF;
        RETURN true;
    END;

BEGIN
    FOR r IN (
        SELECT DISTINCT
            road_number AS r_number,
            road_part_number AS r_p_number
        FROM
            road_address
        ORDER BY
            road_number,
            road_part_number
    ) LOOP
        FOR road IN (
            SELECT
                ra.id,
                road_number,
                road_part_number,
                track_code,
                discontinuity,
                start_addr_m,
                end_addr_m,
                lrm_position_id,
                start_date,
                end_date,
                calibration_points,
                ely,
                road_type,
                t.x AS s_x,
                t.y AS s_y,
                t2.x AS e_x,
                t2.y AS e_y,
                MIN(start_date),
                MAX(end_date)
            FROM
                road_address ra,
                TABLE ( sdo_util.getvertices(geometry) ) t,
                TABLE ( sdo_util.getvertices(geometry) ) t2
            WHERE
                road_number = r.r_number
                AND   road_part_number = r.r_p_number
                AND   terminated = 0
                AND   t.id = 1
                AND   t2.id = 2
            GROUP BY
                ra.id,
                road_number,
                road_part_number,
                track_code,
                discontinuity,
                start_addr_m,
                end_addr_m,
                lrm_position_id,
                start_date,
                end_date,
                calibration_points,
                ely,
                road_type,
                t.x,
                t.y,
                t2.x,
                t2.y
            HAVING start_date >= MIN(start_date)
                   AND nvl(end_date,'01.01.01') <= nvl(MAX(end_date),'01.01.01')
            ORDER BY
                start_addr_m,
                track_code
        ) LOOP
            IF
                current_road != road.road_number OR current_road_part != road.road_part_number
            THEN
                IF
                    current_track > 0 AND check_tracks () = false
                THEN
                    num_errors := num_errors + write_error(road.id,error_overlaping_address);
                    dbms_output.put_line('Houve um erro no check tracks');
                END IF;

                current_track := 0;
                current_road := road.road_number;
                current_road_part := road.road_part_number;
            END IF;
            IF
                road.track_code != current_track
            THEN
                current_track := road.track_code;
                current_end_addr_m := road.end_addr_m;
                IF
                    road.calibration_points = 0
                THEN
                    num_errors := num_errors + write_error(road.id,error_overlaping_address);
                    dbms_output.put_line('Houve um erro nos calibration points');
                END IF;

            ELSE
                IF
                    road.start_addr_m != current_end_addr_m
                THEN
                    num_errors := num_errors + write_error(road.id,error_overlaping_address);
                    dbms_output.put_line('Houve um erro nos m values');
                END IF;
            END IF;
            IF
                current_end_x > 0 AND current_end_y > 0
            THEN
                IF
                      road.s_x != current_end_x AND road.s_y != current_end_y  and  road.e_x != current_end_x AND road.e_y != current_end_y
                THEN
                    IF
                        road.discontinuity NOT IN (
                            2,
                            4
                        )
                    THEN
                        num_errors := num_errors + write_error(road.id,error_topology);
                        dbms_output.put_line('Houve um erro na discontinuidade (devia existir)');
                    ELSE
                        IF
                            road.discontinuity != 5
                        THEN
                            num_errors := num_errors + write_error(road.id,error_topology);
                            dbms_output.put_line('Houve um erro na discontinuidade (não devia existir)');
                        END IF;
                    END IF;

                END IF;
            END IF;
            current_end_x := road.e_x;
            current_end_y := road.e_y;
            current_end_addr_m := road.end_addr_m;
        END LOOP;
    END LOOP;

    dbms_output.put_line('Road network check ended with '
    || num_errors
    || ' errors');
END;