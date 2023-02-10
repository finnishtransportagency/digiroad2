import { KeyProperties } from "../client/kgv-client";
import { ReplaceInfo } from "../client/vkm-client";

const ChangeTypes = {
    add:        "add",
    remove:     "remove",
    replace:    "replace",
    split:      "split"
}

/**
 * Generates JSON from changes
 * @param oldLinks
 * @param newLinks
 * @param replaceInfo
 */
export function generateChangeSet(oldLinks: KeyProperties[], newLinks: KeyProperties[],
                                  replaceInfo: ReplaceInfo[]) {
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
    return withOldLink.concat(withoutOldLink).map( replaces => changeEntry(oldLinks, newLinks, replaces));
}

function changeEntry(allOldLinks: KeyProperties[], allNewLinks: KeyProperties[], replaces: ReplaceInfo[]): ChangeSet {
    const oldLinkId     = replaces.map(value => value.oldLinkId).filter(item => item)[0];
    const newLinkIds    = [...new Set(replaces.map(value => value.newLinkId))].filter(item => item) as string[];

    return {
        changeType:     extractChangeType(newLinkIds, oldLinkId),
        old:            allOldLinks.find(link => link.linkId == oldLinkId) ?? null,
        new:            allNewLinks.filter(link => newLinkIds.includes(link.linkId)),
        replaceInfo:    replaces
    }
}

function extractChangeType(newIds: string[], oldId?: string): string {
    if      (oldId == undefined)    return ChangeTypes.add;
    else if (newIds.length == 0)    return ChangeTypes.remove;
    else if (newIds.length > 1)     return ChangeTypes.split;
    else                            return ChangeTypes.replace;
}

interface GroupedReplaces {
    [key: string]   : ReplaceInfo[];
}

interface ChangeSet {
    changeType      : string;
    old             : KeyProperties | null;
    new             : Array<KeyProperties>;
    replaceInfo     : Array<ReplaceInfo>;
}
