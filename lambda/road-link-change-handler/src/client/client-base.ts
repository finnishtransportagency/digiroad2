import axios, { AxiosInstance } from "axios";
import { fetchSSMParameterValue } from "../service/ssm-service";

const MAX_RETRIES_PER_QUERY = 3;

export async function createInstance(baseUrl: string, apiKeyPath: string, contentType: string = "application/json"): Promise<AxiosInstance> {
    try {
        const apiKeyValue = await fetchSSMParameterValue(apiKeyPath, true);
        return axios.create({
            baseURL: baseUrl,
            headers: {
                "X-API-Key": apiKeyValue,
                "Content-Type": contentType
            }
        });
    } catch (err) {
        console.error(err);
        throw new Error(`Error fetching API Key from ${apiKeyPath}`);
    }
}

/**
 * Get request. Each query is retried for MAX_RETRIES_PER_QUERY times in case of error.
 * @param instance
 * @param url
 * @param params
 * @param retry
 */
export async function getRequest(instance: AxiosInstance, url: string, params: object = {},
                                 retry: number = 1): Promise<any> {
    try {
        const response = await instance.get(url, { params: params });
        return response.data;
    } catch (err) {
        console.error(`Request ${instance.getUri() + url} with params ${JSON.stringify(params)} responded with error:`);
        const errorMsg = processErrorAndExtractMessage(err, instance.getUri() + url);
        if (retry < MAX_RETRIES_PER_QUERY) {
            await exponentialTimeout(retry);
            return await getRequest(instance, url, params, retry + 1);
        } else {
            throw new Error(errorMsg);
        }

    }
}

/**
 * Post request. Each query is retried for MAX_RETRIES_PER_QUERY times in case of error.
 * @param instance
 * @param url
 * @param data
 * @param retry
 */
export async function postRequest(instance: AxiosInstance, url: string, data: object, retry: number = 1): Promise<any> {
    try {
        const response = await instance.post(url, data);
        return response.data;
    } catch (err) {
        console.error(`Request ${instance.getUri() + url} with data ${JSON.stringify(data)} responded with error:`);
        const errorMsg = processErrorAndExtractMessage(err, instance.getUri() + url);
        if (retry < MAX_RETRIES_PER_QUERY) {
            await exponentialTimeout(retry);
            return await postRequest(instance, url, data, retry + 1);
        } else {
            throw new Error(errorMsg);
        }
    }
}

/**
 * Checks if any promise contains an error. If no error is found returns results.
 * @param results
 * @param errorMsg
 */
export function checkResultsForErrors(results: PromiseSettledResult<any>[], errorMsg: string): any[] {
    const errors = results.filter(({ status }) => status === 'rejected') as PromiseRejectedResult[];
    if (errors.length > 0) {
        errors.forEach((error) => console.error(error) );
        throw new Error(errorMsg);
    }
    const fulfilled = results as PromiseFulfilledResult<any>[];
    return fulfilled.map(result => result.value);
}

function processErrorAndExtractMessage(error: any, url: string): string {
    if (axios.isAxiosError(error)) {
        console.error(error.response?.data);
        return `Error happened during fetch of ${url} (${error.response?.status}: ${error.response?.statusText})`;
    } else {
        console.error(error);
        return `Error happened during fetch of ${url}`;
    }
}

async function exponentialTimeout(retry: number) {
    const waitMillis = Math.pow(retry, 2) * 500;
    return new Promise(resolve => setTimeout(resolve, waitMillis));
}