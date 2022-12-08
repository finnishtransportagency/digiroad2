import {SQSEvent} from "aws-lambda";

export const handler = async (event: SQSEvent) => {
    // Replace with lambda logic
    console.info("Run Asset History Processor");
    event.Records.forEach( record => {
        console.info(record.body);
    });
}
