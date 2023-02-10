import { S3, ListObjectsCommandInput, _Object, CompletedPart, CompleteMultipartUploadCommandOutput } from "@aws-sdk/client-s3";
import { checkResultsForErrors } from "../client/client-base";

const Client = new S3({ region: process.env.AWS_REGION });

const BucketName                = process.env.CHANGE_BUCKET;
const MultiPartUploadLimitBytes = 1024 * 1024 * 100;    // 100Mb
const ChunkSizeBytes            = 1024 * 1024 * 5;      // 5Mb

/**
 * S3 Change set naming convention
 * since_until.json i.e. 2022-7-1_2022-7-31.json
 */
export const S3ChangeSet = {
    createKey(since: string, until: string): string {
        function formatDate(date: string) {
            const [day, month, year] = date.split(".");
            return `${year}-${month}-${day}`;
        }
        return `${formatDate(since)}_${formatDate(until)}.json`;
    },
    extractUntilDateFromKey(objectKey: string): Date {
        const dates = objectKey.replace(".json", "");
        const [, until] = dates.split("_");
        return new Date(until);
    },
    isValidKey(objectKey?: string): Boolean {
        if (!objectKey) return false;
        const dates = objectKey.replace(".json", "");
        const [since, until] = dates.split("_");
        return !isNaN(Date.parse(since)) && !isNaN(Date.parse(until));
    }
}

/**
 * Uploads change set to the S3 bucket specified in environment variables
 */
export async function uploadToBucket(since: string, until: string, json: string): Promise<void> {
    if (!BucketName) throw new Error("S3 Bucket not defined");
    const operationStartTime = Date.now();
    const objectKey = S3ChangeSet.createKey(since, until);
    const estimatedFileSize = Buffer.byteLength(json, "utf-8");
    try {
        if (estimatedFileSize <= MultiPartUploadLimitBytes) {
            await uploadObjectToBucket(objectKey, json);
        } else {
            console.info(`Using multipart upload for file size ${estimatedFileSize} (bytes)`);
            await multipartUploadObjectToBucket(objectKey, estimatedFileSize, json);
        }
        console.info(`Saving object ${objectKey} to ${BucketName} took ${(Date.now() - operationStartTime) / 1000} seconds`);
    } catch (e) {
        console.error(e);
        throw new Error(`Failed saving object ${objectKey} to ${BucketName} at ${(Date.now() - operationStartTime) / 1000} seconds`);
    }
}

/**
 * Returns until date of the latest change set or undefined if there is no change sets in the S3 bucket.
 */
export async function getTimeOfLastFetchedChanges(): Promise<Date | undefined> {
    const sortedKeys = await getBucketObjects();
    const newestChangeSetID = sortedKeys.pop();
    if (newestChangeSetID) {
        return  S3ChangeSet.extractUntilDateFromKey(newestChangeSetID);
    }
    console.info("No change sets found")
}

async function getBucketObjects(): Promise<string[]> {
    if (!BucketName) throw new Error("S3 Bucket not defined");
    const params: ListObjectsCommandInput = {
        Bucket: BucketName
    }
    const objects = await listBucketObjects(params);
    const objectKeys = objects.map(object => object.Key).filter(S3ChangeSet.isValidKey) as string[];
    return objectKeys.sort((a, b) => {
        return S3ChangeSet.extractUntilDateFromKey(a).getTime() - S3ChangeSet.extractUntilDateFromKey(b).getTime();
    })
}

async function listBucketObjects(params: ListObjectsCommandInput): Promise<_Object[]> {
    const result = await Client.listObjects(params);
    if (result.IsTruncated && result.Contents) {
        params.Marker = result.NextMarker;
        return result.Contents.concat(await listBucketObjects(params));
    } else {
        return result.Contents ?? [];
    }
}

async function uploadObjectToBucket(key: string, data: string) {
    const params = {
        Bucket: BucketName,
        Key: key,
        Body: data,
        ContentType: "application/json"
    };
    return await Client.putObject(params);
}

async function multipartUploadObjectToBucket(key: string, dataSize: number, data: string): Promise<CompleteMultipartUploadCommandOutput> {
    const chunks = dataToChunks(data, dataSize);
    const uploadId = await createMultipartUpload(key);
    const promises = chunks.map(part => uploadPart(uploadId, key, part.data, part.partNumber));
    const results = await Promise.allSettled(promises);
    const completedParts = checkResultsForErrors(results, "Unable to upload all parts") as CompletedPart[];
    return await completeMultipartUpload(uploadId, key, completedParts);
}

function dataToChunks(data: string, dataSize: number): Array<{partNumber: number, data: Buffer}> {
    const numberOfParts = Math.ceil(dataSize / ChunkSizeBytes);
    const buffer = Buffer.from(data, "utf-8");

    const chunks: Array<{partNumber: number, data: Buffer}> = [];
    for (let i = 0; i < numberOfParts; i++) {
        const chunk = buffer.subarray(i * ChunkSizeBytes, i * ChunkSizeBytes + ChunkSizeBytes);
        chunks.push({ partNumber: i + 1, data: chunk });
    }
    return chunks;
}

/**
 * Initiates a multipart upload and returns an upload ID.
 */
async function createMultipartUpload(key: string): Promise<string> {
    const params = {
        Bucket: BucketName,
        Key: key
    };
    const { UploadId } = await Client.createMultipartUpload(params);
    if (!!UploadId) {
        return UploadId;
    } else {
        throw new Error("Unable to create multipart upload id");
    }
}

/**
 * Tries to upload part to S3. In case of failure, retries three times before error is thrown.
 */
async function uploadPart(uploadId: string, key: string, data: Buffer, partNumber: number,
                          retry: number = 0): Promise<CompletedPart> {
    const params = {
        Bucket: BucketName,
        UploadId: uploadId,
        Key: key,
        PartNumber: partNumber,
        Body: data
    };
    const { ETag } = await Client.uploadPart(params);
    if (ETag) {
        return { ETag: ETag, PartNumber: partNumber };
    } else if (retry < 3) {
        return uploadPart(uploadId, key, data, partNumber, retry + 1);
    } else {
        throw new Error(`Unable to upload part ${partNumber}`);
    }
}

async function completeMultipartUpload(uploadId: string, key: string, completedParts: CompletedPart[]): Promise<CompleteMultipartUploadCommandOutput> {
    const params = {
        Bucket: BucketName,
        UploadId: uploadId,
        Key: key,
        MultipartUpload: { Parts: completedParts }
    };
    return await Client.completeMultipartUpload(params);
}
