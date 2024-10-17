import {SSM} from "@aws-sdk/client-ssm";

const client = new SSM({ region: process.env.AWS_REGION });

export const SsmService = {
    /**
     * Fetch parameter value from parameter store
     * @param name      Name of parameter
     * @param secure    Boolean, is the param secure or not
     * @returns         Parameter value or error
     */
    async fetchSSMParameterValue(name: string | undefined, secure: boolean = true): Promise<string> {
        const params = {
            Name: name || "",
            WithDecryption: secure
        }
        const parameter = await client.getParameter(params);
        const value = parameter.Parameter?.Value;
        if (!value) {
            throw new Error(`Empty value for: ${name}`)
        }
        return value;
    }
}