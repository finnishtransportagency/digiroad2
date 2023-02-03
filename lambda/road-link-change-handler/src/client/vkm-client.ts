import { AxiosInstance } from "axios";
import { createInstance, getRequest, postRequest, checkResultsForErrors } from "./client-base";
import { arrayToChucks } from "../utils/utils";

const VkmApiBase    = process.env.VKM_API_URL;
const VkmApiKey     = process.env.VKM_API_KEY;
const ConvertPath   = "muunna";
const ChangesPath   = "tiekamu";

const MaxBatchSize  = 100;

/**
 * Fetch changes happened during time period
 * @param since
 * @param until
 */
export async function fetchChanges(since: string, until: string): Promise<string[]> {
    if (!VkmApiBase || !VkmApiKey) {
        throw new Error("Either VKM_API_URL or VKM_API_KEY or both environment variables missing");
    }
    const instance = await createInstance(VkmApiBase, VkmApiKey);
    // TODO: Add new parameters for filtering road address changes and for pagination of results(?)
    const json = {
        tilannepvm: since,
        asti: until,
        palautusarvot: "6"
    };
    const results = await getRequest(instance, ChangesPath, json);
    // TODO: mapping of results
    return [];
}

/**
 * Get new situation for changed link ids
 * @param situationDate Date witch situation is desired. Same as until date in fetchChanges
 * @param oldLinkIds    List of old link ids
 */
export async function replacementsForLinks(situationDate: string, oldLinkIds: string[]): Promise<ReplaceInfo[]> {
    if (!VkmApiBase || !VkmApiKey) {
        throw new Error("Either VKM_API_URL, VKM_API_KEY or both environment variables missing");
    }
    const instance = await createInstance(VkmApiBase, VkmApiKey, "application/x-www-form-urlencoded; charset=UTF-8");

    const uniqueIds = [...new Set(oldLinkIds)];
    const batches = arrayToChucks(uniqueIds, MaxBatchSize);
    const promises = batches.map(batch => fetchReplacements(batch, situationDate, instance));
    const results = await Promise.allSettled(promises);
    const msgOnError = "Unable to fetch road links";
    const succeeded = checkResultsForErrors(results, msgOnError) as PromiseFulfilledResult<QueryResult>[];
    return extractReplacements(succeeded);
}

async function fetchReplacements(linkIds: string[], date: string, instance: AxiosInstance): Promise<QueryResult> {
    const json = linkIds.map(linkId => {
        return { kmtk_id: linkId, tilannepvm: date, palautusarvot: "6", valihaku: "true" };
    });
    const result = await postRequest(instance, ConvertPath, {json: JSON.stringify(json)});
    return {
        featureCollection: result,
        queryIds: linkIds
    }
}

function extractReplacements(results: PromiseFulfilledResult<QueryResult>[]): Array<ReplaceInfo> {
    return results.map(result => {
        const collection = result.value.featureCollection;
        const ids = result.value.queryIds;
        let previousIdIndex = 0;
        return collection.features.reduce((array: Array<ReplaceInfo>, feature: Feature) => {
            const properties = feature.properties;
            if (properties.hasOwnProperty('virheet')) {
                previousIdIndex += 1;
                console.error(`${ids[previousIdIndex]}: ${properties.virheet}`);
            } else {
                previousIdIndex = ids.indexOf(properties.kmtk_id_historia);
                array.push(propertiesToReplaceInfo(properties));
            }
            return array;
        }, []);
    }).flat(1)
}

function propertiesToReplaceInfo(properties: Properties): ReplaceInfo {
    return {
        oldLinkId:      properties.kmtk_id_historia,
        newLinkId:      properties.kmtk_id,
        oldFromMValue:  properties.m_arvo_alku_historia,
        oldToMValue:    properties.m_arvo_loppu_historia,
        newFromMValue:  properties.m_arvo,
        newToMValue:    properties.m_arvo_loppu
    }
}

export interface ReplaceInfo {
    oldLinkId               ?: string,
    newLinkId               ?: string,
    oldFromMValue           ?: number,
    oldToMValue             ?: number,
    newFromMValue           ?: number,
    newToMValue             ?: number
}

interface QueryResult {
    featureCollection       : FeatureCollection,
    queryIds                : Array<string>
}

interface FeatureCollection {
    type                    : string,
    features                : Array<Feature>
}

interface Feature {
    type                    : string,
    geometry                : object,
    properties              : Properties
}

interface Properties {
    kmtk_id                 : string
    kmtk_id_loppu           : string,
    kmtk_id_historia        : string
    m_arvo                  : number,
    m_arvo_loppu            : number,
    m_arvo_alku_historia    : number,
    m_arvo_loppu_historia   : number,
    vertikaalisuhde         : number,
    vertikaalisuhde_loppu   : number,
    virheet                 ?: string
}
