import {Client} from "pg";
import {SsmService} from "../service/ssm-service";
import {KgvLink} from "../client/kgv-client";

export class RoadLinkDao {
    private host        = process.env.PG_HOST;
    private port        = parseInt(process.env.PG_PORT || "5432");
    private database    = process.env.PG_DATABASE;
    private user        = process.env.PG_USER;
    private password    = process.env.PG_PW;

    private table       = "kgv_roadlink";

    /**
     * Saves new links and marks old links as expired in same transaction.
     */
    async saveLinkChangesToDb(oldLinkIds: string[], newLinks: KgvLink[]) {
        const dbClient = await this.configureDbClient();
        await dbClient.connect();
        try {
            await dbClient.query('BEGIN');
            await this.expireLinks(dbClient, oldLinkIds);
            for (const newLink of newLinks) {
                await this.insertNewLink(dbClient, newLink);
            }
            await dbClient.query('COMMIT');
        } catch (error) {
            await dbClient.query('ROLLBACK');
            throw new Error("Unable to complete transaction");
        } finally {
            await dbClient.end();
        }
    }

    async configureDbClient(): Promise<Client> {
        const password = this.host == "host.docker.internal" ? this.password : await SsmService.fetchSSMParameterValue(this.password);
        const dbConfig = {
            host: this.host,
            port: this.port,
            database: this.database,
            user: this.user,
            password: password
        };
        return new Client(dbConfig);
    }

    /**
     * Sets expired_date to links
     */
    protected async expireLinks(dbClient: Client, linkIds: string[]): Promise<void> {
        if (linkIds.length) {
            const linkList = `('${linkIds.join("','")}')`;
            const update = `UPDATE ${this.table} SET expired_date = current_timestamp WHERE linkid IN ${linkList}`;
            await dbClient.query(update);
        }
    }

    /**
     * Inserts new link to link table
     */
    protected async insertNewLink(dbClient: Client, link: KgvLink): Promise<void> {
        const insert =
            `INSERT INTO ${this.table} (linkid, mtkid, adminclass, municipalitycode, mtkclass, roadname_fi, roadname_se, 
                    roadnamesme, roadnamesmn, roadnamesms, roadnumber, roadpartnumber, surfacetype, constructiontype, 
                    directiontype, verticallevel, horizontalaccuracy, verticalaccuracy, geometrylength, mtkhereflip, 
                    from_left, to_left, from_right, to_right, created_date, last_edited_date, shape)
            VALUES ('${link.id}', ${link.sourceId}, ${link.adminClass}, ${link.municipality}, ${link.roadClass}, 
                    ${this.optionalStr(link.roadNameFin)}, ${this.optionalStr(link.roadNameSwe)}, 
                    ${this.optionalStr(link.roadNameSme)}, ${this.optionalStr(link.roadNameSmn)}, 
                    ${this.optionalStr(link.roadNameSms)}, ${link.roadNumber}, ${link.roadPartNumber}, ${link.surfaceType}, 
                    ${link.lifeCycleStatus}, ${link.directionType}, ${link.surfaceRelation}, ${link.xyAccuracy}, 
                    ${link.zAccuracy}, ${link.length}, ${link.geometryFlip}, ${link.fromLeft}, ${link.toLeft}, 
                    ${link.fromRight}, ${link.toRight}, ${this.optionalDateTime(link.startTime)}, 
                    ${this.optionalDateTime(link.versionStartTime)}, '${link.geometry}'::geometry)
            ON CONFLICT ON CONSTRAINT ${this.table}_linkid DO NOTHING`;
        await dbClient.query(insert);
    }

    protected optionalStr(property?: string): string | null {
        return property ? `'${property}'` : null;
    }

    protected optionalDateTime(property?: string): string | null {
        return property ? `to_timestamp('${property}', 'YYYY-MM-DD"T"HH24:MI:SS.FF3Z')` : null;
    }
}