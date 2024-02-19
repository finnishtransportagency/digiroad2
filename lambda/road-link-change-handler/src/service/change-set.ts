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
        console.time("extractKeyLinkProperties")
        this.links = links.map(link => this.extractKeyLinkProperties(link));
        console.timeEnd("extractKeyLinkProperties")

        console.time("Group changes")
        const [groupedByOldLinkId, groupedByNewLinkId] = replaceInfo.reduce(([withOldLink, withoutOldLink]: GroupedReplaces[], replace: ReplaceInfo) => {
            if (replace.oldLinkId)
                this.addToMap(replace.oldLinkId, withOldLink, replace);
            else if (replace.newLinkId)
                this.addToMap(replace.newLinkId, withoutOldLink, replace);
            return [withOldLink, withoutOldLink];
        }, [{}, {}]);

        console.timeEnd("Group changes")

        console.time("Extract replaces with old")
        const withOldLink = this.extractReplaces(groupedByOldLinkId);
        console.timeEnd("Extract replaces with old")

        console.time("Extract replaces, no old")
        const withoutOldLink = this.extractReplaces(groupedByNewLinkId);
        console.timeEnd("Extract replaces, no old")

        console.time("Merge replaces")
        const allChanges = withOldLink.concat(withoutOldLink);
        console.timeEnd("Merge replaces")

        console.time("Grouping road links")
        const groupByLinkId = _.chain(this.links).groupBy(p => p.linkId).map((value, key) => ({
            linkId: key, link: value[0]})).value() as GroupByLink[]
        console.timeEnd("Grouping road links")

        this.changeEntries = this.convertToEntries(allChanges, groupByLinkId)
    }

    /**
     *  This is performance critical part of Lambda. Check performance when doing big change. 
     * @param allChanges
     * @param links
     * @private
     */
    private convertToEntries(allChanges:ReplaceInfo[][],links:GroupByLink[]) {
       
            console.time("convertToEntries total time ")
            console.time("Convert to change entries ")
            const convertedArray: ChangeEntry[] = []
            for (const item of allChanges) {
                convertedArray.push(this.toChangeEntry(item, links))
            }
            const converted = convertedArray
            
            console.timeEnd("Convert to change entries ")

            console.time("Combine replace infos for same links ")
            const combined = this.combineReplaceInfos(converted)
            console.timeEnd("Combine replace infos for same links ")

            console.time("Separate Add ")
            const separated = _.partition(combined, p => p.changeType == ChangeTypes.add);
            console.timeEnd("Separate Add ")

            console.time("Filter partial adds ")
            const [add, partialAdd] = _.partition(separated[0], p => { return this.filterPartialAdds(p); });
            console.timeEnd("Filter partial adds ")

            console.time("Correcting incomplete version changes ")
            const [incompleteVersionChanges, rest] = _.partition(separated[1], entry => this.isVersionChange(entry) && !this.mValuesConformToLinkLength(entry, entry.replaceInfo[0]))
            console.info(`Found ${incompleteVersionChanges.length} incomplete version changes`)
            const correctedVersionChanges = this.correctIncompleteVersionChanges(incompleteVersionChanges, partialAdd)
            console.timeEnd("Correcting incomplete version changes ")

            console.time("Merging all changes together ")
            const list = add.concat(correctedVersionChanges).concat(rest)
            console.timeEnd("Merging all changes together ")

            console.timeEnd("convertToEntries total time ")
            return list
    }

    private combineReplaceInfo(continuousParts: ReplaceInfo[], fromPartialAdd: boolean = false) {
        const oldId = continuousParts.map(p => p.oldLinkId).find(id => id != null) || undefined
        const newId = continuousParts.map(p => p.newLinkId).find(id => id != null) || undefined
        const newFromM = _.min(continuousParts.map(p => p.newFromMValue))
        const newToM = _.max(continuousParts.map(p => p.newToMValue))
        const oldFromM = fromPartialAdd ? newFromM : _.min(continuousParts.map(p => p.oldFromMValue))
        const oldToM = fromPartialAdd ? newToM : _.max(continuousParts.map(p => p.oldToMValue))
        return new ReplaceInfo(
            oldId,
            newId,
            _.isNull(oldFromM) ? undefined : oldFromM,
            _.isNull(oldToM) ? undefined : oldToM,
            _.isNull(newFromM) ? undefined : newFromM,
            _.isNull(newToM) ? undefined : newToM
        )
    }

    private combineReplaceInfos(entries: ChangeEntry[]): ChangeEntry[] {
        return entries.map(entry => {
            const isAdd = entry.changeType === ChangeTypes.add
            const groupedReplaceInfo = _.groupBy(entry.replaceInfo, r => [r.oldLinkId, r.newLinkId])
            const mergedReplaceInfo: ReplaceInfo[] = _.flatMap(groupedReplaceInfo, info => {
                if (info.length > 1) {
                    const infoWithoutDuplicates = _.uniqWith(info, _.isEqual)
                    // sort by new mValues for "add" (old mValues are null) and by old mValues for other change types (new mValues may be null)
                    const sortedInfo = isAdd ? _.sortBy(infoWithoutDuplicates, i => i.newToMValue)
                        : _.sortBy(infoWithoutDuplicates, i => i.oldToMValue)
                    // check continuity from new values for "add", and from old values for other change types
                    const continuityCheckSteps = this.checkContinuity(sortedInfo, !isAdd)
                    let continuousParts: ReplaceInfo[] = []
                    // the first part starts always a continuous sequence
                    let currentContinuous: ReplaceInfo[] = [sortedInfo[0]]
                    for (let i = 1; i < sortedInfo.length; i++) {
                        // if the current part is continuous with the previous, add it to the sequence
                        if (continuityCheckSteps[i - 1]) {
                            currentContinuous.push(sortedInfo[i])
                        // otherwise combine the accumulated continuous sequence and start a new sequence
                        } else {
                            if (currentContinuous.length > 0) {
                                continuousParts.push(this.combineReplaceInfo(currentContinuous))
                                currentContinuous = [sortedInfo[i]]
                            }
                        }
                    }
                    // after going through all the parts, combine the last part
                    if (currentContinuous.length > 0) {
                        continuousParts.push(this.combineReplaceInfo(currentContinuous))
                    }
                    return continuousParts
                }
                return info
            })
            return {
                changeType:     entry.changeType,
                old:            entry.old,
                new:            entry.new,
                replaceInfo:    mergedReplaceInfo
            }
        })
    }

    private isVersionChange(entry: ChangeEntry) {
        return entry.old != null && entry.new.length === 1 && entry.old?.linkId.split(":")[0] === entry.new[0].linkId.split(":")[0]
    }

    private mValuesConformToLinkLength(entry: ChangeEntry, replaceInfo: ReplaceInfo) {
        const newFromM = replaceInfo.newFromMValue
        const newToM = replaceInfo.newToMValue
        return newFromM != null && newToM != null && entry.new[0].linkLength != null && Math.abs(Math.abs(newFromM - newToM) - entry.new[0].linkLength) <= 0.1
    }

    private correctIncompleteVersionChanges(entries: ChangeEntry[], partialAdds: ChangeEntry[]): ChangeEntry[] {
        const correctedEntries: [ChangeEntry, boolean][] =  entries.map(entry => {
            const partialAddsForEntry = _.filter(partialAdds, p => p.new.length === 1 && p.new[0].linkId === entry.new[0].linkId)
            const partialAddReplaceInfos = partialAddsForEntry.flatMap(p => p.replaceInfo)
            const allReplaceInfosForLink = _.sortBy(_.uniqWith(entry.replaceInfo.concat(partialAddReplaceInfos), _.isEqual), i => i.newToMValue)
            const checkContinuity = this.checkContinuity(allReplaceInfosForLink)
            if (_.uniq(checkContinuity).length === 1 && checkContinuity[0]) {
                const combinedReplaceInfo = this.combineReplaceInfo(allReplaceInfosForLink, true)
                if (this.mValuesConformToLinkLength(entry, combinedReplaceInfo)) {
                    console.info(`Completed incomplete version change ${JSON.stringify(entry)} with partial add replace 
                    infos ${JSON.stringify(partialAddReplaceInfos)}`)
                    return [{
                        changeType:     entry.changeType,
                        old:            entry.old,
                        new:            entry.new,
                        replaceInfo:    [combinedReplaceInfo]
                    }, true]
                } else {
                    console.error(`Unable to complete version change ${JSON.stringify(entry)}. The partial add replace 
                    infos ${JSON.stringify(partialAddReplaceInfos)} do not conform to the link length.`)
                }
            } else {
                console.error(`Unable to complete version change ${JSON.stringify(entry)}. The partial add replace 
                infos ${JSON.stringify(partialAddReplaceInfos)} do not form a continuous part.`)
            }
            return [entry, false]
        })
        return _.reject(correctedEntries, c => !c[1]).map(c => c[0])
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

    private checkContinuity(infos: ReplaceInfo[], oldValues: boolean = false) {
        const continuityCheckSteps: boolean[] = []
        if (infos.length > 1) {
            for (let i = 0; i < infos.length; i++) {
                const firstItem = infos[i]
                const nextItem = infos[i + 1]
                const partAreDefined = !_.isNil(firstItem) && !_.isNil(nextItem)
                const notContinuous = oldValues ? partAreDefined && firstItem.oldToMValue != nextItem.oldFromMValue
                    : partAreDefined && firstItem.newToMValue != nextItem.newFromMValue;
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

    protected toChangeEntry(change: ReplaceInfo[], links: GroupByLink[]): ChangeEntry {
        const oldLinkIds: string[] = []
        const newLinkIds: Set<string> = new Set()
        const newLinkIdsContainNulls: Set<string | null> = new Set()
        const onlyRelevantLinks: Set<KeyLinkProperties> = new Set()
        for (const item of change) {
            if (item.oldLinkId != null) {
                const oldLink = _.find(links, p => p.linkId == item.oldLinkId)
                oldLinkIds.push(item.oldLinkId);
                if (oldLink?.link != null) onlyRelevantLinks.add(oldLink.link)
            }
            if (item.newLinkId != null) {
                const newLink = _.find(links, p => p.linkId == item.newLinkId)
                newLinkIds.add(item.newLinkId);
                if (newLink?.link != null) onlyRelevantLinks.add(newLink.link)
            }
            newLinkIdsContainNulls.add(item.newLinkId)
        }
        const oldLinkId = oldLinkIds[0];
        const onlyRelevantLinksArray = Array.from(onlyRelevantLinks)
        return {
            changeType:     this.extractChangeType(newLinkIds, oldLinkId, Array.from(newLinkIdsContainNulls)),
            old:            _.find(onlyRelevantLinksArray, (link => link.linkId == oldLinkId)) ?? null,
            new:            _.filter(onlyRelevantLinksArray, (link => _.includes(Array.from(newLinkIds), link.linkId))),
            replaceInfo:    change
        }
    }
    
    private extractChangeType(newIds: Set<string>, oldId: string | null, newLinkIdsContainNulls: (string | null)[]): string {
        const isSplit = newIds.size > 1 || _.filter(newLinkIdsContainNulls, e => e == null).length >= 1
        if (oldId == null)          return ChangeTypes.add;
        else if (newIds.size == 0)  return ChangeTypes.remove;
        else if (isSplit)           return ChangeTypes.split;
        else                        return ChangeTypes.replace;
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
            trafficDirection:   link.directionType,
            lifeCycleStatus:    link.lifeCycleStatus
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

interface GroupByLink {
 linkId: string; 
 link: KeyLinkProperties 
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
    lifeCycleStatus         : number | null;
}
