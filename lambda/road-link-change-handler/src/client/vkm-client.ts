import {AxiosInstance} from "axios";
import array from "lodash";
import {ClientBase} from "./client-base";
import {Utils} from "../utils/utils";

export class VkmClient extends ClientBase {
    private readonly url: string;
    private readonly apiKey: string;

    private readonly convertPath  = "muunna";
    private readonly changesPath  = "tiekamu";

    private readonly maxBatchSize = 100;

    constructor() {
        if (!process.env.VKM_API_URL || !process.env.VKM_API_KEY) {
            throw new Error("Either VKM_API_URL, VKM_API_KEY or both environment variables are missing");
        }
        super();
        this.url    = process.env.VKM_API_URL;
        this.apiKey = process.env.VKM_API_KEY;
    }


    /**
     * Fetch changes happened during time period
     * @param since
     * @param until
     */
    async fetchChanges(since: string, until: string): Promise<string[]> {
        const instance = await this.createInstance(this.url, this.apiKey);
        // TODO: Add new parameters for filtering road address changes and for pagination of results(?)
        const json = {
            tilannepvm: since,
            asti: until,
            palautusarvot: "6"
        };
        const response = await this.getRequest(instance, this.changesPath, json) as TiekamuResponse;
        return response.features.reduce((array: string[], feature: TiekamuFeature) => {
            if (feature.properties.virheet) console.error(`Tiekamu responded with error: ${feature.properties.virheet}`);
            else array.push(feature.properties.kmtk_id);
            return array;
        }, []);
    }

    /**
     * Get new situation for changed link ids
     */
    async replacementsForLinks(since: string, until: string, oldLinkIds: string[]): Promise<ReplaceInfo[]> {
        if (!oldLinkIds.length) return [];

        const instance = await this.createInstance(this.url, this.apiKey, "application/x-www-form-urlencoded; charset=UTF-8");

        const uniqueIds = [...new Set(oldLinkIds)];
        const batches = array.chunk(uniqueIds, this.maxBatchSize);
        const promises = batches.map(batch => this.fetchReplacements(batch, since, until, instance));
        const results = await Promise.allSettled(promises);
        const succeeded = Utils.checkResultsForErrors(results, "Unable to fetch road links") as QueryResult[];
        return this.extractReplacements(succeeded);
    }

    protected async fetchReplacements(linkIds: string[], since: string, until: string, instance: AxiosInstance): Promise<QueryResult> {
        const json = linkIds.map(linkId => {
            return {
                kmtk_id: linkId,
                tilannepvm: since,
                palautusarvot: "6",
                valihaku: "true" };
        });
        const result = await this.postRequest(instance, this.convertPath, {json: JSON.stringify(json)});
        return {
            featureCollection: result,
            queryIds: linkIds
        }
    }

    protected extractReplacements(results: QueryResult[]): Array<ReplaceInfo> {
        return results.map(result => {
            const collection = result.featureCollection;
            const ids = result.queryIds;
            let idIndex = 0;
            return collection.features.reduce((array: Array<ReplaceInfo>, feature: VkmFeature) => {
                const properties = feature.properties;
                if (properties.hasOwnProperty('virheet')) {
                    console.error(`${ids[idIndex]}: ${properties.virheet}`);
                    idIndex += 1;
                } else {
                    if (properties.kmtk_id === properties.kmtk_id_historia) {
                        console.error(`Skipping change. Kmtk_id and kmtk_id_historia are both: ${properties.kmtk_id}`);
                    } else array.push(this.propertiesToReplaceInfo(properties));
                    idIndex = ids.indexOf(properties.kmtk_id_historia) + 1;
                }
                return array;
            }, []);
        }).flat(1);
    }

    protected propertiesToReplaceInfo(properties: VkmProperties): ReplaceInfo {
        return new ReplaceInfo(properties.kmtk_id_historia, properties.kmtk_id, properties.m_arvo_alku_historia,
            properties.m_arvo_loppu_historia, properties.m_arvo, properties.m_arvo_loppu);
    }
}

export class ReplaceInfo {
    oldLinkId           : string | null;
    newLinkId           : string | null;
    oldFromMValue       : number | null;
    oldToMValue         : number | null;
    newFromMValue       : number | null;
    newToMValue         : number | null;
    digitizationChange  : boolean;

    constructor(oldId?: string, newId?: string, oldFromM?: number, oldToM?: number, newFromM?: number, newToM?: number) {
        this.oldLinkId          = oldId ?? null;
        this.newLinkId          = newId ?? null;
        this.oldFromMValue      = oldFromM ?? null;
        this.oldToMValue        = oldToM ?? null;
        this.newFromMValue      = newFromM ?? null;
        this.newToMValue        = newToM ?? null;
        this.digitizationChange = this.digitizationHasChanged(oldFromM, oldToM, newFromM, newToM);
    }

    protected digitizationHasChanged(oldStart?: number, oldEnd?: number,
                                          newStart?: number, newEnd?: number): boolean {
        if (oldStart == undefined || oldEnd == undefined || newStart == undefined || newEnd == undefined) return false;
        return ((oldEnd - oldStart) * (newEnd - newStart)) < 0;
    }
}

interface QueryResult {
    featureCollection       : VkmResponse;
    queryIds                : Array<string>;
}

interface VkmResponse {
    type                    : string;
    features                : Array<VkmFeature>;
}

interface VkmFeature {
    type                    : string;
    geometry                : object;
    properties              : VkmProperties;
}

interface VkmProperties {
    kmtk_id                 : string;
    kmtk_id_loppu           : string;
    kmtk_id_historia        : string;
    m_arvo                  : number;
    m_arvo_loppu            : number;
    m_arvo_alku_historia    : number;
    m_arvo_loppu_historia   : number;
    vertikaalisuhde         : number;
    vertikaalisuhde_loppu   : number;
    virheet                 ?: string;
}

interface TiekamuResponse {
    type                    : string;
    features                : Array<TiekamuFeature>;
}

interface TiekamuFeature {
    type                    : string;
    geometry                : object;
    properties              : TiekamuProperties;
}

interface TiekamuProperties {
    kmtk_id                 : string;
    link_id                 ?: number;
    m_arvo                  : number;
    m_arvo_loppu            : number;
    loppupvm                : string;
    virheet                 ?: string;
}
