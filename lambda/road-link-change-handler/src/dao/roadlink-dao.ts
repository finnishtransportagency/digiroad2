import { Client } from "pg";
import { fetchSSMParameterValue } from "../service/ssm-service";
import { extractLinkGeometry, KgvFeature } from "../client/kgv-client";

const table = "kgv_roadlink";

async function configureDbClient(): Promise<Client> {
    const host = process.env.PG_HOST;
    const password = host == "host.docker.internal" ? process.env.PG_PW : await fetchSSMParameterValue(process.env.PG_PW);
    const dbConfig = {
        host: host,
        port: parseInt(process.env.PG_PORT || "5432"),
        database: process.env.PG_DATABASE,
        user: process.env.PG_USER,
        password: password
    };
    return new Client(dbConfig);
}

/**
 * Saves new links and marks old links as expired in same transaction.
 */
export async function saveLinkChangesToDb(oldLinkIds: string[], newLinks: KgvFeature[]) {
    const dbClient = await configureDbClient();
    await dbClient.connect();
    try {
        await dbClient.query('BEGIN');
        await expireLinks(dbClient, oldLinkIds);
        for (const newLink of newLinks) {
            await insertNewLink(dbClient, newLink);
        }
        await dbClient.query('COMMIT');
    } catch (error) {
        await dbClient.query('ROLLBACK');
        throw new Error("Unable to complete transaction");
    } finally {
        await dbClient.end();
    }
}

/**
 * Sets expired_date to links
 */
async function expireLinks(dbClient: Client, linkIds: string[]): Promise<void> {
    if (linkIds.length) {
        const linkList = `('${linkIds.join("','")}')`;
        const update = `UPDATE ${table} SET expired_date = current_timestamp WHERE linkid IN ${linkList}`;
        await dbClient.query(update);
    }
}

/**
 * Inserts new link to link table
 */
async function insertNewLink(dbClient: Client, link: KgvFeature): Promise<void> {
    const props = link.properties;
    const parsedGeomFlip = props.geometryflip && JSON.parse(props.geometryflip.toString().toLowerCase()) ? 1 : 0;
    const geometryFlip = props.geometryflip ? parsedGeomFlip : null;
    const geometry = extractLinkGeometry(link.geometry);

    const insert =
        `INSERT INTO ${table} (linkid, mtkid, adminclass, municipalitycode, mtkclass, roadname_fi, roadname_se, 
                    roadnamesme, roadnamesmn, roadnamesms, roadnumber, roadpartnumber, surfacetype, constructiontype, 
                    directiontype, verticallevel, horizontalaccuracy, verticalaccuracy, geometrylength, mtkhereflip, 
                    from_left, to_left, from_right, to_right, created_date, last_edited_date, shape)
        VALUES ('${props.id}', ${optionalNum(props.sourceid)}, ${optionalNum(props.adminclass)}, 
                ${optionalNum(props.municipalitycode)}, ${optionalNum(props.roadclass)}, ${optionalStr(props.roadnamefin)},
                ${optionalStr(props.roadnameswe)}, ${optionalStr(props.roadnamesme)}, ${optionalStr(props.roadnamesmn)}, 
                ${optionalStr(props.roadnamesms)}, ${optionalNum(props.roadnumber)}, ${optionalNum(props.roadpartnumber)}, 
                ${optionalNum(props.surfacetype)}, ${optionalNum(props.lifecyclestatus)}, ${optionalNum(props.directiontype)}, 
                ${optionalNum(props.surfacerelation)}, ${optionalNum(props.xyaccuracy)}, ${optionalNum(props.zaccuracy)}, 
                ${optionalNum(props.horizontallength)}, ${geometryFlip}, ${optionalNum(props.addressfromleft)}, 
                ${optionalNum(props.addresstoleft)}, ${optionalNum(props.addressfromright)}, 
                ${optionalNum(props.addresstoright)}, ${optionalDateTime(props.starttime)}, 
                ${optionalDateTime(props.versionstarttime)}, '${geometry}'::geometry)
        ON CONFLICT ON CONSTRAINT ${table}_linkid DO NOTHING`;
    await dbClient.query(insert);
}

function optionalNum(property?: number): number | null {
    return property ? property : null;
}

function optionalStr(property?: string): string | null {
    return property ? `'${property}'` : null;
}

function optionalDateTime(property?: string): string | null {
    return property ? `to_timestamp('${property}', 'YYYY-MM-DD"T"HH24:MI:SS.FF3Z')` : null;
}