import {AxiosInstance} from "axios";
import array from "lodash";
import {ClientBase} from "./client-base";
import {Utils} from "../utils/utils";

export class KgvClient extends ClientBase {
    private readonly url: string;
    private readonly apiKey: string;

    private readonly path = "keskilinjavarasto:road_links_versions/items";

    private readonly maxBatchSize = parseInt(process.env.BATCH_SIZE || '100');

    constructor() {
        if (!process.env.KGV_API_URL || !process.env.KGV_API_KEY) {
            throw new Error("Either KGV_API_URL, KGV_API_KEY or both environment variables are missing");
        }
        super();
        this.url    = process.env.KGV_API_URL;
        this.apiKey = process.env.KGV_API_KEY;
    }

    async fetchRoadLinksByLinkId(linkIds: string[]): Promise<KgvLink[]> {
        if (!linkIds.length) { return []; }

        const instance = await this.createInstance(this.url, this.apiKey);
        const uniqueIds = [...new Set(linkIds)];
        const batches = array.chunk(uniqueIds, this.maxBatchSize);
        const promises = batches.map(batch => this.fetchLinkVersions(batch, instance));
        const results = await Promise.allSettled(promises);
        const succeeded = Utils.checkResultsForErrors(results, "Unable to fetch road links") as KgvFeatureCollection[];
        const links = this.extractLinks(succeeded);
        if (links.length != uniqueIds.length) {
            const retrievedLinks = links.map(link => link.id);
            uniqueIds.forEach(link => {
                if (!retrievedLinks.includes(link)) throw new Error(`Unable to fetch link ${link}`);
            });
        }
        return links;
    }

    protected async fetchLinkVersions(linkIds: string[], instance: AxiosInstance): Promise<KgvFeatureCollection> {
        const params = {
            "filter-lang": "cql-text",
            "crs": "EPSG:3067",
            "filter": `id in ('${linkIds.join("','")}')`
        };
        return await this.getRequest(instance, this.path, params);
    }

    protected extractLinks(results: KgvFeatureCollection[]): Array<KgvLink> {
        return results.map(result => result.features.map(feature => {
            const props = feature.properties;
            const geometry = this.extractLinkGeometry(feature.geometry);
            return new KgvLink(props.id, geometry, props.sourceid, props.adminclass, props.municipalitycode,
                props.horizontallength, props.directiontype, props.roadclass, props.surfacetype, props.roadnamefin,
                props.roadnameswe, props.roadnamesme, props.roadnamesmn, props.roadnamesms, props.roadnumber,
                props.roadpartnumber, props.lifecyclestatus, props.surfacerelation, props.xyaccuracy, props.zaccuracy,
                props.geometryflip, props.addressfromleft, props.addresstoleft, props.addressfromright,
                props.addresstoright, props.starttime, props.versionstarttime);
        })).flat(1);
    }

    protected extractLinkGeometry(geometry: Geometry): string {
        return `SRID=3067;LINESTRING ZM(${geometry.coordinates.map(coord => coord.join(' ')).join(',')})`;
    }
}

export class KgvLink {
    id: string;
    sourceId: number | null;
    adminClass: number | null;
    municipality: number | null;
    roadClass: number | null;
    roadNameFin?: string;
    roadNameSwe?: string;
    roadNameSme?: string;
    roadNameSmn?: string;
    roadNameSms?: string;
    roadNumber: number | null;
    roadPartNumber: number | null;
    surfaceType: number | null;
    lifeCycleStatus: number | null;
    directionType: number | null;
    surfaceRelation: number | null;
    xyAccuracy: number | null;
    zAccuracy: number | null;
    length: number | null;
    geometryFlip: number | null;
    fromLeft: number | null;
    toLeft: number | null;
    fromRight: number | null;
    toRight: number | null;
    startTime?: string;
    versionStartTime?: string;
    geometry: string;

    constructor(id: string, geometry: string, sourceId?: number, adminClass?: number, municipality?: number,
                length?: number, directionType?: number, roadClass?: number, surfaceType?: number, nameFin?: string,
                nameSwe?: string, nameSme?: string, nameSmn?: string, nameSms?: string, roadNumber?: number,
                roadPartNumber?: number, lifeCycleStatus?: number, surfaceRelation?: number, xyAccuracy?: number,
                zAccuracy?: number, geomFlip?: boolean, fromLeft?: number, toLeft?: number, fromRight?: number,
                toRight?: number, startTime?: string, versionStartTime?: string) {
        const roundedLength     = length?.toFixed(3);
        this.id                 = id;
        this.sourceId           = this.extractNumberOrNull(sourceId);
        this.adminClass         = this.extractNumberOrNull(adminClass);
        this.municipality       = this.extractNumberOrNull(municipality);
        this.roadClass          = this.extractNumberOrNull(roadClass);
        this.surfaceType        = this.extractNumberOrNull(surfaceType);
        this.roadNameFin        = nameFin;
        this.roadNameSwe        = nameSwe;
        this.roadNameSme        = nameSme;
        this.roadNameSmn        = nameSmn;
        this.roadNameSms        = nameSms;
        this.roadNumber         = this.extractNumberOrNull(roadNumber);
        this.roadPartNumber     = this.extractNumberOrNull(roadPartNumber);
        this.lifeCycleStatus    = this.extractNumberOrNull(lifeCycleStatus);
        this.directionType      = this.extractNumberOrNull(directionType);
        this.surfaceRelation    = this.extractNumberOrNull(surfaceRelation);
        this.xyAccuracy         = this.extractNumberOrNull(xyAccuracy);
        this.zAccuracy          = this.extractNumberOrNull(zAccuracy);
        this.length             = roundedLength == undefined ? null : parseFloat(roundedLength);
        this.geometryFlip       = geomFlip == undefined ? null : JSON.parse(geomFlip.toString().toLowerCase()) ? 1 : 0;
        this.fromLeft           = this.extractNumberOrNull(fromLeft);
        this.toLeft             = this.extractNumberOrNull(toLeft);
        this.fromRight          = this.extractNumberOrNull(fromRight);
        this.toRight            = this.extractNumberOrNull(toRight);
        this.startTime          = startTime;
        this.versionStartTime   = versionStartTime;
        this.geometry           = geometry;
    }

    protected extractNumberOrNull(property?: number): number | null {
        return property != undefined ? Number(property) : null;
    }
}

interface KgvFeatureCollection {
    type                    : string;
    features                : Array<KgvFeature>;
}

export interface KgvFeature {
    type                    : string;
    id                      : string;
    geometry                : Geometry;
    geometry_name           : string;
    properties              : KgvProperties;
    bbox                    ?: Array<number>;
}

export interface Geometry {
    type                    : string;
    coordinates             : Array<number[]>;
}

interface KgvProperties {
    id                      : string;
    addressfromleft         ?: number;
    addressfromright        ?: number;
    addresstoleft           ?: number;
    addresstoright          ?: number;
    adminclass              ?: number;
    datasource              ?: number;  /* Not used by Digiroad */
    directiontype           ?: number;
    endtime                 ?: string;  /* Not used by Digiroad */
    featureclass            ?: string;  /* Not used by Digiroad */
    geometryflip            ?: boolean;
    horizontallength        ?: number;
    kmtkid                  : string;   /* Not used by Digiroad */
    lifecyclestatus         ?: number;
    municipalitycode        ?: number;
    roadclass               ?: number;
    roadnamefin             ?: string;
    roadnamesme             ?: string;
    roadnamesmn             ?: string;
    roadnamesms             ?: string;
    roadnameswe             ?: string;
    roadnumber              ?: number;
    roadpartnumber          ?: number;
    sourceid                ?: number;
    sourcemodificationtime  ?: string;  /* Not used by Digiroad */
    starttime               ?: string;
    state                   ?: number;  /* Not used by Digiroad */
    surfacerelation         ?: number;
    surfacetype             ?: number;
    updatereason            ?: number;  /* Not used by Digiroad */
    version                 : number;   /* Not used by Digiroad */
    versionendtime          ?: string;  /* Not used by Digiroad */
    versionstarttime        ?: string;
    widthtype               ?: number;  /* Not used by Digiroad */
    xyaccuracy              ?: number;
    zaccuracy               ?: number;
}
