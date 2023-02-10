

export const handler = async (event: Event) => {
    const [since, until] = changeTimeFrame(event);
    console.info(`Fetching changes since ${since} until ${until}`);

    // Replace with lambda logic
}

/**
 * If since and until are defined in event and are valid dates, use them.
 * Otherwise, determine timeframe from s3. (NOT IMPLEMENTED YET)
 */
function changeTimeFrame(event: Event): [string, string] {
    if (event.since && event.until) {
        const sinceDate = new Date(event.since);
        const untilDate = new Date(event.until);

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

interface Event {
    since ?: string,
    until ?: string;
}
