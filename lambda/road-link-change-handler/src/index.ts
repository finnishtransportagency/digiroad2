
export const handler = async () => {
    const [since, until] = changeTimeFrame();
    console.info(`Fetching changes since ${since.toISOString()} until ${until.toISOString()}`);

    // Replace with lambda logic
}
/**
 * If since and until are defined in env variables and are valid dates, use them.
 * Otherwise, determine timeframe from s3. (NOT IMPLEMENTED YET)
 */
function changeTimeFrame(): [Date, Date] {
    if (!!process.env.CHANGES_SINCE && !!process.env.CHANGES_UNTIL) {
        const sinceDate = new Date(process.env.CHANGES_SINCE);
        const untilDate = new Date(process.env.CHANGES_UNTIL);

        // Return env dates if they are valid and since date is before until date
        if(!isNaN(Number(sinceDate)) && !isNaN(Number(untilDate)) && sinceDate < untilDate) {
            return [sinceDate, untilDate];
        }
    }
    const since = new Date();
    since.setDate(since.getDate() - 1);
    const until = new Date();
    return [since, until];
}
