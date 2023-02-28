import {VkmClient} from "./client/vkm-client";
import {KgvClient} from "./client/kgv-client";
import {ChangeSet} from "./service/change-set";
import {S3Service} from "./service/s3-service";
import {RoadLinkDao} from "./dao/road-link-dao";

const s3Service     = new S3Service();
const vkmClient     = new VkmClient();
const kgvClient     = new KgvClient();
const roadLinkDao   = new RoadLinkDao();

// Test links are used until Tiekamu is working properly
const testLinkIds = [
    "3ef8bd3a-6ded-4b41-8f9f-a380e549d4b8:1", "002636da-cba4-4b54-b6ae-b63fdda83ddc:1", "85307792-d4aa-4850-9f0e-2d71af394716:1",
    "042f0006-1e0e-4215-91ec-f9b2d048295a:1", "4285a8a3-ddc7-4f89-9f24-88ea95ed2ec2:1", "c646dd83-6437-4a05-93c0-a459884313f9:1",
    "41a67ca5-886f-44ff-a9ca-92d7d9bea129:1", "7ce91531-42e8-424b-a528-b72af5cb1180:1", "9d23b85a-d7bf-4c6f-83ff-aa391ff4879f:1",
    "b05075a5-45e1-447e-9813-752ba3e07fe5:1", "2c7f5289-b9f0-4c0e-8aa8-5c6c0fa092ec:1", "424cfc65-5e87-4f4b-8d39-dbdfa501d126:1",
    "f4510e27-1583-4c40-b1ad-fa614b818e4d:1", "020e05d1-de71-43a6-ac30-7c342dcc16ba:1", "f11dcd9e-73d6-49f1-a1c3-bede80fc768d:1"
];

export const handler = async (event: Event) => {
    const [since, until] = await s3Service.getChangeTimeframe(event);
    console.info(`Fetching changes since ${since} until ${until}`);

    // TODO: Commented out until Tiekamu is working properly
    const changedLinkIds = testLinkIds; //await vkmClient.fetchChanges(since, until);
    console.info(`${changedLinkIds.length} changes returned`);

    const replacements = await vkmClient.replacementsForLinks(since, until, changedLinkIds);
    console.info(`Got ${replacements.length} replacements`);

    const oldLinkIds = replacements.map(replacement => replacement.oldLinkId).filter(value => value != undefined) as string[];
    const newLinkIds = replacements.map(replacement => replacement.newLinkId).filter(value => value != undefined) as string[];

    const newLinks = await kgvClient.fetchRoadLinksByLinkId(newLinkIds);
    const oldLinks = await kgvClient.fetchRoadLinksByLinkId(oldLinkIds);

    const changeSet = new ChangeSet(oldLinks, newLinks, replacements).toJson();

    // TODO: Commented out until Tiekamu is working properly
    //await roadLinkDao.saveLinkChangesToDb(oldLinkIds, newLinks);
    //await s3Service.uploadToBucket(since, until, changeSet);
}

export interface Event {
    since ?: string;
    until ?: string;
}
