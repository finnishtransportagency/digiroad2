import {KgvLink} from "../client/kgv-client";
import {ReplaceInfo} from "../client/vkm-client";

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
        const groupedByOldLinkId = replaceInfo.reduce((group: GroupedReplaces, replace: ReplaceInfo) => {
            const groupKey = replace.oldLinkId;
            if (groupKey) {
                group[groupKey] = group[groupKey] ?? [];
                group[groupKey].push(replace);
            }
            return group;
        }, {});
        const withOldLink = Object.entries(groupedByOldLinkId).map(([, replaces]) => replaces);
        const withoutOldLink = replaceInfo.filter(replace => !replace.oldLinkId).map(replace => [replace]);
        const allChanges = withOldLink.concat(withoutOldLink);
        this.changeEntries = allChanges.map(change => this.toChangeEntry(change));
    }

    toJson(): string {
        return JSON.stringify(this.changeEntries);
    }

    protected toChangeEntry(change: ReplaceInfo[]): ChangeEntry {
        const oldLinkId     = change.map(value => value.oldLinkId).filter(item => item)[0];
        const newLinkIds    = [...new Set(change.map(value => value.newLinkId))].filter(item => item) as string[];

        return {
            changeType:     this.extractChangeType(newLinkIds, oldLinkId),
            old:            this.links.find(link => link.linkId == oldLinkId) ?? null,
            new:            this.links.filter(link => newLinkIds.includes(link.linkId)),
            replaceInfo:    change
        }
    }

    protected extractChangeType(newIds: string[], oldId: string | null): string {
        if      (oldId == null)         return ChangeTypes.add;
        else if (newIds.length == 0)    return ChangeTypes.remove;
        else if (newIds.length > 1)     return ChangeTypes.split;
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
            trafficDirection:   link.directionType
        }
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
    trafficDirection        : number | null;
}
