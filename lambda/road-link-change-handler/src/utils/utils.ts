/**
 * Transforms date to string DD.MM.YYYY in Helsinki time
 * @param date
 */
export function dateToDateString(date: Date): string {
    return date.toLocaleDateString('fi-FI', {timeZone: 'Europe/Helsinki'});
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