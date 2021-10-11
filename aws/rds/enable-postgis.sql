create extension postgis;
create extension fuzzystrmatch;
create extension postgis_tiger_geocoder;
create extension postgis_topology;

alter schema tiger owner to digiroad2postgres;
alter schema tiger_data owner to digiroad2postgres;
alter schema topology owner to digiroad2postgres;

CREATE FUNCTION exec(text) returns text language plpgsql volatile AS $f$ BEGIN EXECUTE $1; RETURN $1; END; $f$;      

SELECT exec('ALTER TABLE ' || quote_ident(s.nspname) || '.' || quote_ident(s.relname) || ' OWNER TO digiroad2postgres;')
  FROM (
    SELECT nspname, relname
    FROM pg_class c JOIN pg_namespace n ON (c.relnamespace = n.oid)
    WHERE nspname in ('tiger','topology') AND
    relkind IN ('r','S','v') ORDER BY relkind = 'S') as s;
