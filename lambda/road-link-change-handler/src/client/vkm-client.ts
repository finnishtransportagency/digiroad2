import {ClientBase} from "./client-base";

export class VkmClient extends ClientBase {
    private readonly url: string;
    private readonly apiKey: string;

    private readonly changesPath  = "tiekamu";

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
    async fetchChanges(since: string, until: string): Promise<ReplaceInfo[]> {
        const instance = await this.createInstance(this.url, this.apiKey);
        const json = {
            tilannepvm: since,
            kohdepvm: until,
            palautusarvot: "7"
        };
        const response = await this.getRequest(instance, this.changesPath, json) as TiekamuResponse;
        return response.features.reduce((array: ReplaceInfo[], feature: TiekamuFeature) => {
            if (feature.properties.virheet) {
                console.error(`Tiekamu responded with error: ${feature.properties.virheet}`);
            } else if (feature.properties.link_id === feature.properties.link_id_kohdepvm) {
                //console.warn(`Skipping change. link_id and link_id_kohdepvm are both: ${feature.properties.link_id }`);
            } else array.push(this.tiekamuResponseToReplaceInfo(feature.properties));
            return array;
        }, []);
    }

    protected tiekamuResponseToReplaceInfo(properties: TiekamuProperties): ReplaceInfo {
        return new ReplaceInfo(
            properties.link_id,
            properties.link_id_kohdepvm,
            properties.m_arvo_alku,
            properties.m_arvo_loppu,
            properties.m_arvo_alku_kohdepvm,
            properties.m_arvo_loppu_kohdepvm
        );
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

    protected digitizationHasChanged(oldStart?: number, oldEnd?: number, newStart?: number, newEnd?: number): boolean {
        if (oldStart == undefined || oldEnd == undefined || newStart == undefined || newEnd == undefined) return false;
        return ((oldEnd - oldStart) * (newEnd - newStart)) < 0;
    }
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
    link_id                 ?: string;
    m_arvo_alku             ?: number;
    m_arvo_loppu            ?: number;
    link_id_kohdepvm        ?: string;
    m_arvo_alku_kohdepvm    ?: number;
    m_arvo_loppu_kohdepvm   ?: number;
    virheet                 ?: string;
}
