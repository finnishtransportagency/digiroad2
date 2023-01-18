import { SQSEvent } from "aws-lambda";
import { Client } from "pg";
import { fetchSSMParameterValue } from "./aws/ssm-service";
import { processChanges } from "./processor/change-processor";

async function configureDbClient(): Promise<Client> {
    const host = process.env.PG_HOST;
    const password = host == "host.docker.internal" ? process.env.PG_PW : await fetchSSMParameterValue(process.env.PG_PW);
    const dbConfig = {
        host: host,
        port: parseInt(process.env.PG_PORT || "5432"),
        database: process.env.PG_DATABASE,
        user: process.env.PG_USER,
        password: password
    };
    return new Client(dbConfig);
}

export const handler = async (event: SQSEvent) => {
    const dbClient = await configureDbClient();
    try {
        await dbClient.connect();
        const processed = await processChanges(event.Records, dbClient);
        console.info(`Successfully processed ${processed.success.length}/${event.Records.length} messages`);
        return { batchItemFailures: processed.error }
    } catch (e) {
        throw e;
    } finally {
        await dbClient.end()
    }
}
