import {ReplaceInfo, VkmClient} from "./client/vkm-client";
import {KgvClient} from "./client/kgv-client";
import {ChangeSet} from "./service/change-set";
import {S3Service} from "./service/s3-service";
import {RoadLinkDao} from "./dao/road-link-dao";

const s3Service     = new S3Service();
const vkmClient     = new VkmClient();
const kgvClient     = new KgvClient();
const roadLinkDao   = new RoadLinkDao();

export const handler = async (event: Event) => {
    const [since, until] = await s3Service.getChangeTimeframe(event);
    console.info(`Fetching changes since ${since} until ${until}`);

    const replacements: ReplaceInfo[] = await vkmClient.fetchChanges(since, until);
    console.info(`Got ${replacements.length} replacements`);

    const oldLinkIds = replacements.map(replacement => replacement.oldLinkId).filter(value => value != undefined) as string[];
    const newLinkIds = replacements.map(replacement => replacement.newLinkId).filter(value => value != undefined) as string[];
    console.info(`Fetching ${newLinkIds.concat(oldLinkIds).length} links`);
    const links = await kgvClient.fetchRoadLinksByLinkId(newLinkIds.concat(oldLinkIds));
    const newLinks = links.filter(link => newLinkIds.includes(link.id));
    console.info(`Got ${newLinks.length} links`);
    const changeSet = new ChangeSet(links, replacements);
    const changeSetString = changeSet.toJson();
    console.info(`Got ${changeSet.changeEntries.length} changes`);
    //console.log(changeSetString)
    // TODO: Commented out until Tiekamu is working properly
    //await roadLinkDao.saveLinkChangesToDb(oldLinkIds, newLinks);  // Save links to Digiroad db
    //await s3Service.uploadToBucket(since, until, changeSetString);      // Put change set to s3
}

export interface Event {
    since ?: string;
    until ?: string;
}
