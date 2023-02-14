import { AxiosInstance } from "axios";
import { createInstance, getRequest, checkResultsForErrors } from "./client-base";
import { arrayToChucks } from "../utils/utils";

const Url       = process.env.KGV_API_URL;
const ApiKey    = process.env.KGV_API_KEY;
const Path      = "keskilinjavarasto:road_links_versions/items";

const MaxBatchSize  = 100;

export async function fetchRoadLinksByLinkId(linkIds: string[]): Promise<KgvFeature[]> {
    if (!Url || !ApiKey) {
        throw new Error("Either KGV_API_URL, KGV_API_KEY or both environment variables missing");
    }
    const instance = await createInstance(Url, ApiKey);

    const uniqueIds = [...new Set(linkIds)];
    const batches = arrayToChucks(uniqueIds, MaxBatchSize);
    const promises = batches.map(batch => fetchLinkVersions(batch, instance));
    const results = await Promise.allSettled(promises);
    const msgOnError = "Unable to fetch road links";
    const succeeded = checkResultsForErrors(results, msgOnError) as KgvFeatureCollection[];
    const links =  extractLinks(succeeded);
    if (links.length != uniqueIds.length) {
        const retrievedLinks = links.map(link => link.properties.id);
        uniqueIds.forEach(link => {
            if (!retrievedLinks.includes(link)) throw new Error(`Unable to fetch link ${link}`);
        });
    }
    return links;
}

async function fetchLinkVersions(linkIds: string[], instance: AxiosInstance): Promise<KgvFeatureCollection> {
    const params = {
        "filter-lang": "cql-text",
        "crs": "EPSG:3067",
        "filter": `id in ('${linkIds.join("','")}')`
    };
    return await getRequest(instance, Path, params);
}

function extractLinks(results: KgvFeatureCollection[]): Array<KgvFeature> {
    return results.map(result => result.features).flat(1);
}

export function extractKeyProperties(link: KgvFeature): KeyProperties {
    return {
        linkId:             link.properties.id,
        linkLength:         link.properties.horizontallength,
        geometry:           extractLinkGeometry(link.geometry),
        roadClass:          Number(link.properties.roadclass),
        adminClass:         link.properties.adminclass ? Number(link.properties.adminclass) : null,
        municipality:       Number(link.properties.municipalitycode),
        trafficDirection:   Number(link.properties.directiontype)
    }
}

export function extractLinkGeometry(geometry: Geometry): string {
    return `SRID=3067;LINESTRING ZM(${geometry.coordinates.map(coord => coord.join(' ')).join(',')})`;
}

export interface KeyProperties {
    linkId                  : string,
    linkLength              : number,
    geometry                : string,
    roadClass               : number,
    adminClass              : number | null,
    municipality            : number,
    trafficDirection        : number
}

interface KgvFeatureCollection {
    type                    : string,
    features                : Array<KgvFeature>
}

export interface KgvFeature {
    type                    : string,
    id                      : string,
    geometry                : Geometry,
    geometry_name           : string,
    properties              : KgvProperties,
    bbox                    : Array<number>
}

interface Geometry {
    type                    : string,
    coordinates             : Array<number[]>
}

interface KgvProperties {
    id                      : string,
    addressfromleft         ?: number,
    addressfromright        ?: number,
    addresstoleft           ?: number,
    addresstoright          ?: number,
    adminclass              ?: number,
    datasource              : number,   /* Not used by Digiroad */
    directiontype           : number,
    endtime                 ?: string,  /* Not used by Digiroad */
    featureclass            : string,   /* Not used by Digiroad */
    geometryflip            : boolean,
    horizontallength        : number,
    kmtkid                  : string,   /* Not used by Digiroad */
    lifecyclestatus         : number,
    municipalitycode        : number,
    roadclass               : number,
    roadnamefin             ?: string,
    roadnamesme             ?: string,
    roadnamesmn             ?: string,
    roadnamesms             ?: string,
    roadnameswe             ?: string,
    roadnumber              ?: number,
    roadpartnumber          ?: number,
    sourceid                : number,
    sourcemodificationtime  ?: string,  /* Not used by Digiroad */
    starttime               : string,
    state                   ?: number,  /* Not used by Digiroad */
    surfacerelation         : number,
    surfacetype             : number,
    updatereason            ?: number,  /* Not used by Digiroad */
    version                 : number,   /* Not used by Digiroad */
    versionendtime          ?: string,  /* Not used by Digiroad */
    versionstarttime        : string,
    widthtype               ?: number,  /* Not used by Digiroad */
    xyaccuracy              : number,
    zaccuracy               : number
}
