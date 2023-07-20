import {KgvLink} from "../client/kgv-client";
import {ReplaceInfo} from "../client/vkm-client";
import _ from "lodash";

const ChangeTypes = {
    add:        "add",
    remove:     "remove",
    replace:    "replace",
    split:      "split"
}

export class ChangeSet {
    changeEntries: ChangeEntry[];

    private readonly links: KeyLinkProperties[];

    constructor(links: KgvLink[], replaceInfo: ReplaceInfo[]) {
        this.links = links.map(link => this.extractKeyLinkProperties(link));
        const [groupedByOldLinkId, groupedByNewLinkId] = replaceInfo.reduce(([withOldLink, withoutOldLink]: GroupedReplaces[], replace: ReplaceInfo) => {
            if (replace.oldLinkId)
                this.addToMap(replace.oldLinkId, withOldLink, replace);
            else if (replace.newLinkId)
                this.addToMap(replace.newLinkId, withoutOldLink, replace);
            return [withOldLink, withoutOldLink];
        }, [{}, {}]);
        const withOldLink = this.extractReplaces(groupedByOldLinkId);
        const withoutOldLink = this.extractReplaces(groupedByNewLinkId);
        const allChanges = withOldLink.concat(withoutOldLink);
        const converted = allChanges.map(change => this.toChangeEntry(change));
        const separated = _.partition(converted,p=>p.changeType == ChangeTypes.add);
        const add = _.filter(separated[0], p=> {return this.filterPartialAdds(p);});
        this.changeEntries = separated[1].concat(add);
    }

    private filterPartialAdds(p: ChangeEntry) {
        let continuity: boolean = true
        const sorted = _.sortBy(p.replaceInfo, (a => a.newToMValue))
        const startPart = sorted[0]?.newFromMValue
        const endPart = _.last(sorted)?.newToMValue
        const newLinkLength = p.new[0].linkLength
        const continuityCheckSteps = this.checkContinuity(sorted);

        const partitionByTrueOrFalse = _.partition(continuityCheckSteps,p=>!p)
        if (partitionByTrueOrFalse[0].length >= 1) {
            continuity = false
            if (partitionByTrueOrFalse[1].length >= 1) {
                console.warn("Some part of change entries are not contiguous")
                console.warn(this.convertToJson(p.replaceInfo))
            }
        }
        return endPart == newLinkLength && startPart == 0 && continuity
    }

    private checkContinuity(infos: ReplaceInfo[]) {
        const continuityCheckSteps: boolean[] = []
        if (infos.length > 1) {
            for (let i = 0; i < infos.length; i++) {
                const firstItem = infos[i]
                const nextItem = infos[i + 1]
                const partAreDefined = !_.isNil(firstItem) && !_.isNil(nextItem)
                const notContinuous = partAreDefined && firstItem.newToMValue != nextItem.newFromMValue;
                if (notContinuous) {
                    continuityCheckSteps.push(false)
                } else if (partAreDefined) {
                    continuityCheckSteps.push(true)
                }
            }
        }
        return continuityCheckSteps
    }

    toJson(): string {
        return JSON.stringify(this.changeEntries);
    }
    convertToJson(input: object): string {
        return JSON.stringify(input);
    }

    protected toChangeEntry(change: ReplaceInfo[]): ChangeEntry {
        const oldLinkId     = change.map(value => value.oldLinkId).filter(item => item)[0];
        const newLinkIds    = [...new Set(change.map(value => value.newLinkId))].filter(item => item) as string[];
        const newLinkIdsContainNulls    = [...new Set(change.map(value => value.newLinkId))] as string[];
        
        return {
            changeType:     this.extractChangeType(newLinkIds, oldLinkId,newLinkIdsContainNulls),
            old:            this.links.find(link => link.linkId == oldLinkId) ?? null,
            new:            this.links.filter(link => newLinkIds.includes(link.linkId)),
            replaceInfo:    change
        }
    }

    protected extractChangeType(newIds: string[], oldId: string | null, newLinkIdsContainNulls:string[]): string {
        const isSplit = newIds.length > 1 || _.filter(newLinkIdsContainNulls,e=>e == null).length >= 1
        if      (oldId == null)         return ChangeTypes.add;
        else if (newIds.length == 0)    return ChangeTypes.remove;
        else if (isSplit)               return ChangeTypes.split;
        else                            return ChangeTypes.replace;
    }

    protected extractKeyLinkProperties(link: KgvLink): KeyLinkProperties {
        return {
            linkId:             link.id,
            linkLength:         link.length,
            geometry:           link.geometry,
            roadClass:          link.roadClass,
            adminClass:         link.adminClass,
            municipality:       link.municipality,
            surfaceType:        link.surfaceType,
            trafficDirection:   link.directionType
        }
    }

    protected addToMap(groupKey: string, groups: GroupedReplaces, replace: ReplaceInfo): void {
        groups[groupKey] = groups[groupKey] ?? [];
        groups[groupKey].push(replace);
    }

    protected extractReplaces(groupedReplaces: GroupedReplaces): ReplaceInfo[][] {
        return Object.entries(groupedReplaces).map(([, replaces]) => replaces);
    }
}

interface GroupedReplaces {
    [key: string]   : ReplaceInfo[];
}

interface ChangeEntry {
    changeType      : string;
    old             : KeyLinkProperties | null;
    new             : Array<KeyLinkProperties>;
    replaceInfo     : Array<ReplaceInfo>;
}

export interface KeyLinkProperties {
    linkId                  : string;
    linkLength              : number | null;
    geometry                : string;
    roadClass               : number | null;
    adminClass              : number | null;
    municipality            : number | null;
    surfaceType             : number | null;
    trafficDirection        : number | null;
}
