import { SQSRecord } from "aws-lambda";
import { Client } from "pg";

export async function processChanges(records: SQSRecord[], client: Client): Promise<ProcessChangesResult> {
    const result: ProcessChangesResult = {
        success: [],
        error: []
    };

    for (const record of records) {
        if (result.error.length > 0) {
            // If previous message has failed, return messageId directly to the error list.
            result.error.push({ itemIdentifier: record.messageId });
            continue;
        }
        try {
            const change = parseMessageBody(record);
            await saveChange(change, client);
            result.success.push(record.messageId);
        } catch (e) {
            console.error(e);
            console.error(`Unable to process message ${record.messageId}.`);
            console.error(record.body);
            result.error.push({ itemIdentifier: record.messageId });
        }
    }
    return result;
}

async function saveChange(change: ChangeRecord, client: Client) {
    const endMValue = change.endMValue || '';
    const value = change.value || '';
    const valueType = change.value && change.valueType ? change.valueType : '';
    const insert =
        `INSERT INTO change_table (id, edit_date, edit_by, change_type, asset_type_id, asset_id, start_m_value, 
                          end_m_value, value, value_type, link_id, side_code)
         VALUES (nextval('primary_key_seq'), to_timestamp('${change.editDate}', 'YYYY-MM-DD"T"HH24:MI:SS.FF3'), 
                 '${change.editBy}', ${change.changeType}, ${change.assetTypeId}, ${change.id}, 
                 ${change.startMValue}, NULLIF('${endMValue}', '')::numeric, NULLIF('${value}', '')::varchar, 
                 NULLIF('${valueType}', '')::varchar, '${change.linkId}', ${change.sideCode})
         ON CONFLICT ON CONSTRAINT unique_change_event DO NOTHING`;

    await client.query({ text: insert });
}

function parseMessageBody(record: SQSRecord): ChangeRecord {
    const message = record.body;
    if (typeof message === "string") {
        return JSON.parse(message) as ChangeRecord;
    } else {
        return message as ChangeRecord;
    }
}

interface ChangeRecord {
    editDate: string,
    editBy: string,
    changeType: number,
    assetTypeId: number,
    id: number,
    linkId: string,
    sideCode: number,
    startMValue: number,
    endMValue?: number,
    value?: string,
    valueType?: string
}

interface ProcessChangesResult {
    success: string[],
    error: Array<{ itemIdentifier: string }>
}