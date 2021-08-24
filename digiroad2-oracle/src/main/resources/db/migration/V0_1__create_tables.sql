
CREATE TABLE additional_panel
(
    id                           bigint,
    asset_id                     bigint NOT NULL,
    property_id                  bigint NOT NULL,
    additional_sign_type         bigint,
    additional_sign_value        varchar(128),
    additional_sign_info         varchar(128),
    form_position                bigint,
    additional_sign_text         varchar(128),
    additional_sign_size         bigint,
    additional_sign_coating_type bigint,
    additional_sign_panel_color  bigint
);

CREATE TABLE additional_panel_history
(
    id                           bigint NOT NULL,
    asset_id                     bigint NOT NULL,
    property_id                  bigint NOT NULL,
    additional_sign_type         bigint,
    additional_sign_value        varchar(128),
    additional_sign_info         varchar(128),
    form_position                bigint,
    additional_sign_text         varchar(128),
    additional_sign_size         bigint,
    additional_sign_coating_type bigint,
    additional_sign_panel_color  bigint
);

CREATE TABLE administrative_class
(
    id                       bigint    NOT NULL,
    mml_id                   bigint,
    link_id                  numeric(38),
    administrative_class     bigint,
    vvh_administrative_class bigint,
    created_date             timestamp NOT NULL DEFAULT LOCALTIMESTAMP,
    modified_by              varchar(128),
    created_by               varchar(128),
    valid_to                 timestamp
);

CREATE TABLE asset
(
    id                 bigint    NOT NULL,
    external_id        bigint,
    asset_type_id      bigint,
    created_date       timestamp NOT NULL DEFAULT current_timestamp,
    created_by         varchar(128),
    modified_date      timestamp,
    modified_by        varchar(128),
    bearing            bigint,
    valid_from         timestamp,
    valid_to           timestamp,
    geometry           geometry,
    municipality_code  bigint,
    floating           boolean DEFAULT '0',
    area               numeric(38),
    verified_by        varchar(128),
    verified_date      timestamp,
    information_source numeric(38)
);

CREATE TABLE asset_history
(
    id                 bigint    NOT NULL,
    external_id        bigint,
    asset_type_id      bigint,
    created_date       timestamp NOT NULL DEFAULT current_timestamp,
    created_by         varchar(128),
    modified_date      timestamp,
    modified_by        varchar(128),
    bearing            bigint,
    valid_from         timestamp,
    valid_to           timestamp,
    geometry           geometry,
    municipality_code  bigint,
    floating           boolean DEFAULT '0',
    area               numeric(38),
    verified_by        varchar(128),
    verified_date      timestamp,
    information_source numeric(38)
);

CREATE TABLE asset_link
(
    asset_id    bigint,
    position_id numeric(38)
);

CREATE TABLE asset_link_history
(
    asset_id    bigint,
    position_id numeric(38)
);

CREATE TABLE asset_type
(
    id            bigint       NOT NULL,
    name          varchar(512) NOT NULL,
    geometry_type varchar(128) NOT NULL,
    created_date  timestamp    NOT NULL DEFAULT current_timestamp,
    created_by    varchar(128),
    modified_date timestamp,
    modified_by   varchar(128),
    verifiable    bigint DEFAULT 0
);

CREATE TABLE connected_asset
(
    linear_asset_id bigint    NOT NULL,
    point_asset_id  bigint    NOT NULL,
    created_date    timestamp NOT NULL DEFAULT LOCALTIMESTAMP,
    modified_date   timestamp,
    valid_to        timestamp
);

CREATE TABLE connected_asset_history
(
    linear_asset_id bigint    NOT NULL,
    point_asset_id  bigint    NOT NULL,
    created_date    timestamp NOT NULL DEFAULT LOCALTIMESTAMP,
    modified_date   timestamp,
    valid_to        timestamp
);

CREATE TABLE dashboard_info
(
    municipality_id    numeric(22),
    asset_type_id      numeric(22),
    modified_by        varchar(128),
    last_modified_date timestamp
);

CREATE TABLE date_period_value
(
    id          bigint    NOT NULL,
    asset_id    bigint    NOT NULL,
    property_id bigint    NOT NULL,
    start_date  timestamp NOT NULL,
    end_date    timestamp NOT NULL
);

CREATE TABLE date_period_value_history
(
    id          bigint    NOT NULL,
    asset_id    bigint    NOT NULL,
    property_id bigint    NOT NULL,
    start_date  timestamp NOT NULL,
    end_date    timestamp NOT NULL
);

CREATE TABLE date_property_value
(
    id          bigint    NOT NULL,
    asset_id    bigint    NOT NULL,
    property_id bigint    NOT NULL,
    date_time   timestamp NOT NULL
);

CREATE TABLE date_property_value_history
(
    id          bigint    NOT NULL,
    asset_id    bigint    NOT NULL,
    property_id bigint    NOT NULL,
    date_time   timestamp NOT NULL
);

CREATE TABLE ely
(
    id       smallint     NOT NULL,
    name_fi  varchar(512) NOT NULL,
    name_sv  varchar(512) NOT NULL,
    geometry geometry,
    zoom     smallint
);

CREATE TABLE enumerated_value
(
    id            bigint       NOT NULL,
    property_id   bigint       NOT NULL,
    value         numeric,
    name_fi       varchar(512) NOT NULL,
    name_sv       varchar(512),
    image_id      bigint,
    created_date  timestamp    NOT NULL DEFAULT current_timestamp,
    created_by    varchar(128),
    modified_date timestamp,
    modified_by   varchar(128)
);

CREATE TABLE export_lock
(
    id          numeric(38) NOT NULL,
    description varchar(4000)
);

CREATE TABLE export_report
(
    id              numeric(38) NOT NULL,
    content         text,
    file_name       varchar(512),
    exported_assets varchar(2048),
    municipalities  varchar(2048),
    status          smallint    NOT NULL DEFAULT 1,
    created_by      varchar(256),
    created_date    timestamp   NOT NULL DEFAULT LOCALTIMESTAMP
);

CREATE TABLE feedback
(
    id           bigint    NOT NULL,
    created_by   varchar(128),
    created_date timestamp NOT NULL DEFAULT current_timestamp,
    subject      varchar(128),
    body         varchar(4000),
    status       boolean DEFAULT '0',
    status_date  timestamp DEFAULT current_timestamp
);

CREATE TABLE functional_class
(
    mml_id           bigint,
    functional_class integer   NOT NULL,
    modified_date    timestamp NOT NULL DEFAULT current_timestamp,
    modified_by      varchar(128),
    link_id          numeric(38),
    id               bigint    NOT NULL
);

CREATE TABLE import_log
(
    id           numeric(38) NOT NULL,
    content      text,
    import_type  varchar(50),
    file_name    varchar(128),
    status       smallint    NOT NULL DEFAULT 1,
    created_date timestamp   NOT NULL DEFAULT LOCALTIMESTAMP,
    created_by   varchar(128)
);

CREATE TABLE inaccurate_asset
(
    asset_id             bigint,
    asset_type_id        bigint    NOT NULL,
    municipality_code    integer,
    administrative_class smallint,
    created_date         timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    link_id              numeric(38)
);

CREATE TABLE incomplete_link
(
    mml_id               bigint,
    municipality_code    bigint,
    administrative_class bigint,
    link_id              numeric(38),
    id                   bigint NOT NULL
);

CREATE TABLE lane
(
    id                bigint       NOT NULL,
    lane_code         numeric(20)  NOT NULL,
    created_date      timestamp    NOT NULL,
    created_by        varchar(128) NOT NULL,
    modified_date     timestamp,
    modified_by       varchar(128),
    expired_date      timestamp,
    expired_by        varchar(128),
    valid_from        timestamp,
    valid_to          timestamp,
    municipality_code bigint
);

CREATE TABLE lane_attribute
(
    id            bigint NOT NULL,
    lane_id       bigint,
    name          varchar(128),
    value         varchar(128),
    required      boolean DEFAULT '0',
    created_date  timestamp,
    created_by    varchar(128),
    modified_date timestamp,
    modified_by   varchar(128)
);

CREATE TABLE lane_history
(
    id                   bigint       NOT NULL,
    new_id               bigint,
    old_id               bigint,
    lane_code            numeric(20)  NOT NULL,
    created_date         timestamp    NOT NULL,
    created_by           varchar(128) NOT NULL,
    modified_date        timestamp,
    modified_by          varchar(128),
    expired_date         timestamp,
    expired_by           varchar(128),
    valid_from           timestamp,
    valid_to             timestamp,
    municipality_code    bigint,
    history_created_date timestamp    NOT NULL,
    history_created_by   varchar(128) NOT NULL
);

CREATE TABLE lane_history_attribute
(
    id              bigint NOT NULL,
    lane_history_id bigint,
    name            varchar(128),
    value           varchar(128),
    required        boolean DEFAULT '0',
    created_date    timestamp,
    created_by      varchar(128),
    modified_date   timestamp,
    modified_by     varchar(128)
);

CREATE TABLE lane_history_link
(
    lane_id          bigint NOT NULL,
    lane_position_id bigint NOT NULL
);

CREATE TABLE lane_history_position
(
    id                 bigint           NOT NULL,
    side_code          integer          NOT NULL,
    start_measure      double precision NOT NULL,
    end_measure        double precision NOT NULL,
    link_id            bigint,
    adjusted_timestamp numeric(38),
    modified_date      timestamp
);

CREATE TABLE lane_link
(
    lane_id          bigint NOT NULL,
    lane_position_id bigint NOT NULL
);

CREATE TABLE lane_position
(
    id                 bigint           NOT NULL,
    side_code          integer          NOT NULL,
    start_measure      double precision NOT NULL,
    end_measure        double precision NOT NULL,
    link_id            bigint,
    adjusted_timestamp numeric(38),
    modified_date      timestamp
);

CREATE TABLE link_type
(
    mml_id        bigint,
    link_type     integer   NOT NULL,
    modified_date timestamp NOT NULL DEFAULT current_timestamp,
    modified_by   varchar(128),
    link_id       numeric(38),
    id            bigint    NOT NULL
);

CREATE TABLE localized_string
(
    id            numeric(38) NOT NULL,
    value_fi      varchar(256),
    value_sv      varchar(256),
    created_date  timestamp   NOT NULL,
    created_by    varchar(128),
    modified_date timestamp   NOT NULL DEFAULT current_timestamp,
    modified_by   varchar(128)
);

CREATE TABLE lrm_position
(
    id                 numeric(38) NOT NULL,
    lane_code          integer,
    side_code          integer,
    start_measure      double precision,
    end_measure        double precision,
    mml_id             bigint,
    link_id            numeric(38),
    adjusted_timestamp numeric(38) NOT NULL DEFAULT 0,
    modified_date      timestamp   NOT NULL DEFAULT current_timestamp,
    link_source        numeric(38) DEFAULT (1)
);

CREATE TABLE lrm_position_history
(
    id                 numeric(38) NOT NULL,
    lane_code          integer,
    side_code          integer,
    start_measure      double precision,
    end_measure        double precision,
    mml_id             bigint,
    link_id            numeric(38),
    adjusted_timestamp numeric(38) NOT NULL DEFAULT 0,
    modified_date      timestamp   NOT NULL DEFAULT current_timestamp,
    link_source        numeric(38) DEFAULT (1)
);

CREATE TABLE manoeuvre
(
    id              bigint    NOT NULL,
    additional_info varchar(4000),
    type            smallint  NOT NULL,
    modified_date   timestamp DEFAULT (null),
    modified_by     varchar(128),
    valid_to        timestamp,
    created_by      varchar(128),
    created_date    timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    traffic_sign_id bigint,
    suggested       boolean DEFAULT '0'
);

CREATE TABLE manoeuvre_element
(
    manoeuvre_id bigint NOT NULL,
    element_type integer     NOT NULL,
    mml_id       bigint,
    link_id      numeric(38),
    dest_link_id numeric(38)
);

CREATE TABLE manoeuvre_element_history
(
    manoeuvre_id bigint NOT NULL,
    element_type integer     NOT NULL,
    mml_id       bigint,
    link_id      numeric(38),
    dest_link_id numeric(38)
);

CREATE TABLE manoeuvre_exceptions
(
    manoeuvre_id  bigint NOT NULL,
    exception_type smallint    NOT NULL
);

CREATE TABLE manoeuvre_exceptions_history
(
    manoeuvre_id   bigint NOT NULL,
    exception_type smallint    NOT NULL
);

CREATE TABLE manoeuvre_history
(
    id              bigint    NOT NULL,
    additional_info varchar(4000),
    type            smallint  NOT NULL,
    modified_date   timestamp DEFAULT (null),
    modified_by     varchar(128),
    valid_to        timestamp,
    created_by      varchar(128),
    created_date    timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    traffic_sign_id bigint,
    suggested       boolean DEFAULT '0'
);

CREATE TABLE manoeuvre_validity_period
(
    id           bigint      NOT NULL,
    manoeuvre_id bigint      NOT NULL,
    type         numeric(38) NOT NULL,
    start_hour   numeric(38) NOT NULL,
    end_hour     numeric(38) NOT NULL,
    start_minute numeric(38) NOT NULL DEFAULT 0,
    end_minute   numeric(38) NOT NULL DEFAULT 0
);

CREATE TABLE manoeuvre_val_period_history
(
    id           bigint      NOT NULL,
    manoeuvre_id bigint      NOT NULL,
    type         numeric(38) NOT NULL,
    start_hour   numeric(38) NOT NULL,
    end_hour     numeric(38) NOT NULL,
    start_minute numeric(38) NOT NULL DEFAULT 0,
    end_minute   numeric(38) NOT NULL DEFAULT 0
);

CREATE TABLE multiple_choice_value
(
    id                  bigint NOT NULL,
    property_id         bigint NOT NULL,
    enumerated_value_id bigint NOT NULL,
    asset_id            bigint NOT NULL,
    modified_date       timestamp,
    modified_by         varchar(128),
    grouped_id          bigint DEFAULT 0
);

CREATE TABLE multiple_choice_value_history
(
    id                  bigint NOT NULL,
    property_id         bigint NOT NULL,
    enumerated_value_id bigint NOT NULL,
    asset_id            bigint NOT NULL,
    modified_date       timestamp,
    modified_by         varchar(128),
    grouped_id          bigint DEFAULT 0
);

CREATE TABLE municipality
(
    id                 numeric(22) NOT NULL,
    name_fi            varchar(128),
    name_sv            varchar(128),
    ely_nro            bigint,
    road_maintainer_id bigint,
    geometry           geometry,
    zoom               smallint
);

CREATE TABLE municipality_dataset
(
    dataset_id     char(36)  NOT NULL,
    geojson        text      NOT NULL,
    roadlinks      text      NOT NULL,
    received_date  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_date timestamp,
    status         bigint
);

CREATE TABLE municipality_feature
(
    dataset_id char(36)      NOT NULL,
    status     varchar(4000),
    feature_id varchar(4000) NOT NULL
);

CREATE TABLE municipality_verification
(
    id                     bigint NOT NULL,
    municipality_id        bigint,
    asset_type_id          bigint,
    verified_date          timestamp,
    verified_by            varchar(128),
    valid_to               timestamp,
    modified_by            varchar(128),
    last_user_modification varchar(128),
    last_date_modification timestamp,
    number_of_assets       numeric(38) DEFAULT 0,
    refresh_date           timestamp,
    suggested_assets       varchar(4000)
);

CREATE TABLE number_property_value
(
    id          bigint NOT NULL,
    asset_id    bigint NOT NULL,
    property_id bigint NOT NULL,
    value       numeric,
    grouped_id  bigint DEFAULT 0
);

CREATE TABLE number_property_value_history
(
    id          bigint NOT NULL,
    asset_id    bigint NOT NULL,
    property_id bigint NOT NULL,
    value       numeric,
    grouped_id  bigint DEFAULT 0
);

CREATE TABLE prohibition_exception
(
    id                   bigint      NOT NULL,
    prohibition_value_id bigint      NOT NULL,
    type                 numeric(38) NOT NULL
);

CREATE TABLE prohibition_exception_history
(
    id                   bigint      NOT NULL,
    prohibition_value_id bigint      NOT NULL,
    type                 numeric(38) NOT NULL
);

CREATE TABLE prohibition_validity_period
(
    id                   bigint      NOT NULL,
    prohibition_value_id bigint      NOT NULL,
    type                 numeric(38) NOT NULL,
    start_hour           numeric(38) NOT NULL,
    end_hour             numeric(38) NOT NULL,
    start_minute         numeric(38) NOT NULL DEFAULT 0,
    end_minute           numeric(38) NOT NULL DEFAULT 0
);

CREATE TABLE prohibition_value
(
    id              bigint      NOT NULL,
    asset_id        bigint      NOT NULL,
    type            numeric(38) NOT NULL,
    additional_info varchar(4000)
);

CREATE TABLE prohibition_value_history
(
    id              bigint      NOT NULL,
    asset_id        bigint      NOT NULL,
    type            numeric(38) NOT NULL,
    additional_info varchar(4000)
);

CREATE TABLE proh_val_period_history
(
    id                   bigint      NOT NULL,
    prohibition_value_id bigint      NOT NULL,
    type                 numeric(38) NOT NULL,
    start_hour           numeric(38) NOT NULL,
    end_hour             numeric(38) NOT NULL,
    start_minute         numeric(38) NOT NULL DEFAULT 0,
    end_minute           numeric(38) NOT NULL DEFAULT 0
);

CREATE TABLE property
(
    id                       bigint    NOT NULL,
    asset_type_id            bigint    NOT NULL,
    property_type            varchar(128),
    required                 boolean DEFAULT '0',
    created_date             timestamp NOT NULL DEFAULT current_timestamp,
    created_by               varchar(128),
    modified_date            timestamp,
    modified_by              varchar(128),
    name_localized_string_id numeric(38),
    public_id                varchar(256),
    default_value            varchar(256),
    max_value_length         bigint
);

CREATE TABLE road_link_attributes
(
    id                 bigint      NOT NULL,
    link_id            numeric(38),
    name               varchar(128),
    value              varchar(128),
    created_date       timestamp   NOT NULL DEFAULT LOCALTIMESTAMP,
    created_by         varchar(128),
    modified_date      timestamp,
    modified_by        varchar(128),
    valid_to           timestamp,
    mml_id             bigint,
    adjusted_timestamp numeric(38) NOT NULL DEFAULT 0
);

CREATE TABLE service_area
(
    id       smallint NOT NULL,
    geometry geometry,
    zoom     smallint
);

CREATE TABLE service_point_value
(
    id                  bigint      NOT NULL,
    asset_id            bigint      NOT NULL,
    type                numeric(38) NOT NULL,
    additional_info     varchar(4000),
    parking_place_count bigint,
    name                varchar(128),
    type_extension      bigint,
    is_authority_data   boolean DEFAULT NULL,
    weight_limit        bigint
);

CREATE TABLE service_point_value_history
(
    id                  bigint      NOT NULL,
    asset_id            bigint      NOT NULL,
    type                numeric(38) NOT NULL,
    additional_info     varchar(4000),
    parking_place_count bigint,
    name                varchar(128),
    type_extension      bigint,
    is_authority_data   boolean DEFAULT NULL,
    weight_limit        bigint
);

CREATE TABLE service_user
(
    id            bigint       NOT NULL,
    username      varchar(256) NOT NULL,
    configuration varchar(4000),
    name          varchar(256),
    created_at    timestamp,
    modified_at   timestamp
);

CREATE TABLE single_choice_value
(
    asset_id            bigint NOT NULL,
    enumerated_value_id bigint NOT NULL,
    property_id         bigint NOT NULL,
    modified_date       timestamp,
    modified_by         varchar(128),
    grouped_id          bigint NOT NULL DEFAULT 0
);

CREATE TABLE single_choice_value_history
(
    asset_id            bigint NOT NULL,
    enumerated_value_id bigint NOT NULL,
    property_id         bigint NOT NULL,
    modified_date       timestamp,
    modified_by         varchar(128),
    grouped_id          bigint NOT NULL DEFAULT 0
);

CREATE TABLE temporary_id
(
    id bigint NOT NULL
);

CREATE TABLE temp_road_address_info
(
    id                bigint    NOT NULL,
    link_id           bigint    NOT NULL,
    municipality_code bigint    NOT NULL,
    road_number       bigint    NOT NULL,
    road_part         bigint    NOT NULL,
    track_code        bigint    NOT NULL,
    start_address_m   bigint    NOT NULL,
    end_address_m     bigint    NOT NULL,
    start_m_value     bigint    NOT NULL,
    end_m_value       bigint    NOT NULL,
    side_code         numeric(38),
    created_date      timestamp NOT NULL DEFAULT LOCALTIMESTAMP,
    created_by        varchar(128)
);

CREATE TABLE terminal_bus_stop_link
(
    terminal_asset_id bigint,
    bus_stop_asset_id bigint
);

CREATE TABLE text_property_value
(
    id            bigint    NOT NULL,
    asset_id      bigint    NOT NULL,
    property_id   bigint    NOT NULL,
    value_fi      varchar(4000),
    value_sv      varchar(4000),
    created_date  timestamp NOT NULL DEFAULT current_timestamp,
    created_by    varchar(128),
    modified_date timestamp,
    modified_by   varchar(128),
    grouped_id    bigint DEFAULT 0
);

CREATE TABLE text_property_value_history
(
    id            bigint    NOT NULL,
    asset_id      bigint    NOT NULL,
    property_id   bigint    NOT NULL,
    value_fi      varchar(4000),
    value_sv      varchar(4000),
    created_date  timestamp NOT NULL DEFAULT current_timestamp,
    created_by    varchar(128),
    modified_date timestamp,
    modified_by   varchar(128),
    grouped_id    bigint DEFAULT 0
);

CREATE TABLE traffic_direction
(
    mml_id            bigint,
    traffic_direction integer   NOT NULL,
    modified_date     timestamp NOT NULL DEFAULT current_timestamp,
    modified_by       varchar(128),
    link_id           numeric(38),
    id                bigint    NOT NULL,
    link_type         integer
);

CREATE TABLE traffic_sign_manager
(
    traffic_sign_id      bigint NOT NULL,
    linear_asset_type_id bigint NOT NULL,
    sign                 text
);

CREATE TABLE unknown_speed_limit
(
    municipality_code    integer,
    administrative_class smallint,
    link_id              numeric(38) NOT NULL,
    mml_id               numeric(38),
    unnecessary          numeric(38) NOT NULL DEFAULT 0
);

CREATE TABLE user_notification
(
    id           bigint NOT NULL,
    created_date timestamp DEFAULT LOCALTIMESTAMP,
    heading      varchar(256),
    content      varchar(4000)
);

CREATE TABLE validity_period_property_value
(
    id              bigint      NOT NULL,
    asset_id        bigint      NOT NULL,
    property_id     bigint      NOT NULL,
    type            numeric(38),
    period_week_day numeric(38) NOT NULL,
    start_hour      numeric(38) NOT NULL,
    end_hour        numeric(38) NOT NULL,
    start_minute    numeric(38) NOT NULL DEFAULT 0,
    end_minute      numeric(38) NOT NULL DEFAULT 0
);

CREATE TABLE vallu_xml_ids
(
    id           bigint NOT NULL,
    asset_id     bigint NOT NULL,
    created_date timestamp DEFAULT LOCALTIMESTAMP
);

CREATE TABLE val_period_property_value_hist
(
    id              bigint      NOT NULL,
    asset_id        bigint      NOT NULL,
    property_id     bigint      NOT NULL,
    type            numeric(38),
    period_week_day numeric(38) NOT NULL,
    start_hour      numeric(38) NOT NULL,
    end_hour        numeric(38) NOT NULL,
    start_minute    numeric(38) NOT NULL DEFAULT 0,
    end_minute      numeric(38) NOT NULL DEFAULT 0
);
