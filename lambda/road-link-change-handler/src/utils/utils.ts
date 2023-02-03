/**
 * Transforms date to string DD.MM.YYYY
 * @param date
 */
export function dateToDateString(date: Date): string {
    return `${ date.getDate() }.${ date.getMonth() + 1 }.${ date.getFullYear() }`;
}

/**
 * Splits array to smaller chunks
 * @param array     Array containing strings
 * @param chunkSize Maximum number of items in one array
 */
export function arrayToChucks(array: string[], chunkSize: number): Array<string[]> {
    const result = [];
    for (let i = 0; i < array.length; i += chunkSize) {
        result.push(array.slice(i, i + chunkSize));
    }
    return result;
}