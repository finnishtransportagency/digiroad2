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

    // TODO: Commented out until Tiekamu is working properly
    const replacements: ReplaceInfo[] = await vkmClient.fetchChanges(since, until);
    console.info(`Got ${replacements.length} replacements`);

    const oldLinkIds = replacements.map(replacement => replacement.oldLinkId).filter(value => value != undefined) as string[];
    const newLinkIds = replacements.map(replacement => replacement.newLinkId).filter(value => value != undefined) as string[];

    const links = await kgvClient.fetchRoadLinksByLinkId(newLinkIds.concat(oldLinkIds));

    const changeSet = new ChangeSet(links, replacements).toJson();

    // TODO: Commented out until Tiekamu is working properly
    //const newLinks = links.filter(link => newLinkIds.includes(link.id));
    //await roadLinkDao.saveLinkChangesToDb(oldLinkIds, newLinks);
    //await s3Service.uploadToBucket(since, until, changeSet);
}

export interface Event {
    since ?: string;
    until ?: string;
}
