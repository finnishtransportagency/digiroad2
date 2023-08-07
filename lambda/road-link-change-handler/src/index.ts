import {ReplaceInfo, VkmClient} from "./client/vkm-client";
import {KgvClient, KgvLink} from "./client/kgv-client";
import {ChangeSet} from "./service/change-set";
import {S3Service} from "./service/s3-service";
import {RoadLinkDao} from "./dao/road-link-dao";
import _ from "lodash";

const s3Service     = new S3Service();
const vkmClient     = new VkmClient();
const kgvClient     = new KgvClient();
const roadLinkDao   = new RoadLinkDao();

export const handler = async (event: Event) => {
    const [since, until] = await s3Service.getChangeTimeframe(event);
    console.info(`Fetching changes since ${since} until ${until}`);

    console.time("Fetching changes")
    const replacements: ReplaceInfo[] = await vkmClient.fetchChanges(since, until);
    console.timeEnd("Fetching changes")
    console.info(`Got ${replacements.length} replacements`);
    
    console.time("Filter undefined away")
    const oldLinkIds = replacements.map(replacement => replacement.oldLinkId).filter(value => value != undefined) as string[];
    const newLinkIds = replacements.map(replacement => replacement.newLinkId).filter(value => value != undefined) as string[];
    console.timeEnd("Filter undefined away")
    console.info(`Fetching ${newLinkIds.concat(oldLinkIds).length} links`);
    
    console.time("Fetch KGV history links")
    const links = await kgvClient.fetchRoadLinksByLinkId(newLinkIds.concat(oldLinkIds));
    console.timeEnd("Fetch KGV history links")
    
    console.time("Filter to only new links")
    
    function filterFunction(links: KgvLink[]) {
        const split = _.chunk(links,1000)
        let filtered:KgvLink[] = []
        split.forEach(item=> {filtered = filtered.concat(item.filter(link => newLinkIds.includes(link.id)))})
        return filtered;
    }
    const newLinks = filterFunction(links);
    
    console.timeEnd("Filter to only new links")
    
    console.info(`Got ${newLinks.length} new links`);
    
    console.time("Total times to create changes")
    const changeSet = new ChangeSet(links, replacements);
    console.timeEnd("Total times to create changes")
    
    console.time("Convert to String")
    const changeSetString = changeSet.toJson();
    console.timeEnd("Convert to String")
    
    console.info(`Got ${changeSet.changeEntries.length} changes`);
    
    //console.log(changeSetString)
    // TODO: Commented out until Tiekamu is working properly
    //console.time("Save new links and expire old")
    //await roadLinkDao.saveLinkChangesToDb(oldLinkIds, newLinks);  // Save links to Digiroad db
    //console.timeEnd("Save new links and expire old")
    //console.time("Load to S3")
    //await s3Service.uploadToBucket(since, until, changeSetString);      // Put change set to s3
    //console.timeEnd("Load to S3")
}

export interface Event {
    since ?: string;
    until ?: string;
}
