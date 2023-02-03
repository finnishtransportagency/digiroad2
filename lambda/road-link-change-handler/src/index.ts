
export const handler = async () => {
    const [since, until] = changeTimeFrame();
    console.info(`Fetching changes since ${since} until ${until}`);

    // Replace with lambda logic
}
/**
 * If since and until are defined in env variables and are valid dates, use them.
 * Otherwise, determine timeframe from s3. (NOT IMPLEMENTED YET)
 */
function changeTimeFrame(): [string, string] {
    if (!!process.env.CHANGES_SINCE && !!process.env.CHANGES_UNTIL) {
        const sinceDate = new Date(process.env.CHANGES_SINCE);
        const untilDate = new Date(process.env.CHANGES_UNTIL);

        // Return env dates if they are valid and since date is before until date
        if(!isNaN(Number(sinceDate)) && !isNaN(Number(untilDate)) && sinceDate < untilDate) {
            return [dateToDateString(sinceDate), dateToDateString(untilDate)];
        }
    }
    const since = new Date();
    since.setDate(since.getDate() - 1);
    const until = new Date();
    return [dateToDateString(since), dateToDateString(until)];
}
