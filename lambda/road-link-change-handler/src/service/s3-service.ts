import {S3, ListObjectsCommandInput, _Object, CompletedPart, CompleteMultipartUploadCommandOutput} from "@aws-sdk/client-s3";
import {Event} from "../index";
import {Utils} from "../utils/utils";
import {S3ObjectUtils} from "../utils/s3-object-utils";

const S3Client = new S3({ region: process.env.AWS_REGION });

const MultiPartUploadLimitBytes = 1024 * 1024 * 100;    // 100Mb
const ChunkSizeBytes            = 1024 * 1024 * 5;      // 5Mb

export class S3Service {
    bucketName: string;

    constructor() {
        if (!process.env.CHANGE_BUCKET) throw new Error("S3 Bucket not defined")
        this.bucketName = process.env.CHANGE_BUCKET;
    }

    /**
     * Uploads change set to the S3 bucket specified in environment variables
     */
    async uploadToBucket(since: string, until: string, json: string): Promise<void> {
        const operationStartTime = Date.now();
        const objectKey = S3ObjectUtils.createKey(since, until);
        const estimatedFileSize = Buffer.byteLength(json, "utf-8");
        try {
            if (estimatedFileSize <= MultiPartUploadLimitBytes) {
                await this.uploadObjectToBucket(objectKey, json);
            } else {
                console.info(`Using multipart upload for file size ${estimatedFileSize} (bytes)`);
                await this.multipartUploadObjectToBucket(objectKey, estimatedFileSize, json);
            }
            console.info(`Saving object ${objectKey} to ${this.bucketName} took ${(Date.now() - operationStartTime) / 1000} seconds`);
        } catch (e) {
            console.error(e);
            throw new Error(`Failed saving object ${objectKey} to ${this.bucketName} at ${(Date.now() - operationStartTime) / 1000} seconds`);
        }
    }

    /**
     * Determines timeframe to new change set.
     * If since and until are defined in event and are valid dates, use them. Otherwise, determine timeframe from s3.
     */
    async getChangeTimeframe(event: Event): Promise<[string, string]> {
        if (event.since && event.until) {
            const sinceDate = Date.parse(event.since);
            const untilDate = Date.parse(event.until);

            // Return env dates if they are valid and since date is before until date
            if(!isNaN(sinceDate) && !isNaN(untilDate) && sinceDate <= untilDate) {
                return [Utils.dateToDateString(new Date(sinceDate)), Utils.dateToDateString(new Date(untilDate))];
            } else {
                throw new Error(`Invalid dates provided (since: ${event.since}, until: ${event.until})`);
            }
        }
        const since = await this.getTimeOfLastFetchedChanges() ?? new Date("2022-05-10");
        since.setDate(since.getDate() + 1);
        const until = new Date(since.getDate() + 1);
        return [Utils.dateToDateString(since), Utils.dateToDateString(until)];
    }

    /**
     * Returns until date of the latest change set or undefined if there is no change sets in the S3 bucket.
     */
    protected async getTimeOfLastFetchedChanges(): Promise<Date | undefined> {
        const sortedKeys = await this.getBucketObjects();
        const newestChangeSetID = sortedKeys[0];
        if (newestChangeSetID) {
            return  S3ObjectUtils.extractUntilDateFromKey(newestChangeSetID);
        }
        console.info("No change sets found");
    }

    protected async getBucketObjects(): Promise<string[]> {
        const params: ListObjectsCommandInput = {
            Bucket: this.bucketName
        }
        const objects = await this.listBucketObjects(params);
        const objectKeys = objects.map(object => object.Key);
        return S3ObjectUtils.sortKeysByUntilDate(objectKeys);
    }

    protected async listBucketObjects(params: ListObjectsCommandInput): Promise<_Object[]> {
        const result = await S3Client.listObjects(params);
        if (result.IsTruncated && result.Contents) {
            params.Marker = result.NextMarker;
            return result.Contents.concat(await this.listBucketObjects(params));
        } else {
            return result.Contents ?? [];
        }
    }

    protected async uploadObjectToBucket(key: string, data: string) {
        const params = {
            Bucket: this.bucketName,
            Key: key,
            Body: data,
            ContentType: "application/json"
        };
        return await S3Client.putObject(params);
    }

    protected async multipartUploadObjectToBucket(key: string, dataSize: number, data: string): Promise<CompleteMultipartUploadCommandOutput> {
        const chunks = this.dataToChunks(data, dataSize);
        const uploadId = await this.createMultipartUpload(key);
        const promises = chunks.map(part => this.uploadPart(uploadId, key, part.data, part.partNumber));
        const results = await Promise.allSettled(promises);
        const completedParts = Utils.checkResultsForErrors(results, "Unable to upload all parts") as CompletedPart[];
        return await this.completeMultipartUpload(uploadId, key, completedParts);
    }

    protected dataToChunks(data: string, dataSize: number): Array<{partNumber: number, data: Buffer}> {
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
    protected async createMultipartUpload(key: string): Promise<string> {
        const params = {
            Bucket: this.bucketName,
            Key: key
        };
        const { UploadId } = await S3Client.createMultipartUpload(params);
        if (!!UploadId) {
            return UploadId;
        } else {
            throw new Error("Unable to create multipart upload id");
        }
    }

    /**
     * Tries to upload part to S3. In case of failure, retries three times before error is thrown.
     */
    protected async uploadPart(uploadId: string, key: string, data: Buffer, partNumber: number,
                              retry: number = 0): Promise<CompletedPart> {
        const params = {
            Bucket: this.bucketName,
            UploadId: uploadId,
            Key: key,
            PartNumber: partNumber,
            Body: data
        };
        const { ETag } = await S3Client.uploadPart(params);
        if (ETag) {
            return { ETag: ETag, PartNumber: partNumber };
        } else if (retry < 3) {
            return this.uploadPart(uploadId, key, data, partNumber, retry + 1);
        } else {
            throw new Error(`Unable to upload part ${partNumber}`);
        }
    }

    protected async completeMultipartUpload(uploadId: string, key: string, completedParts: CompletedPart[]): Promise<CompleteMultipartUploadCommandOutput> {
        const params = {
            Bucket: this.bucketName,
            UploadId: uploadId,
            Key: key,
            MultipartUpload: { Parts: completedParts }
        };
        return await S3Client.completeMultipartUpload(params);
    }
}
