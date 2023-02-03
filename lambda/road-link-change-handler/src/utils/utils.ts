/**
 * Transforms date to string DD.MM.YYYY
 * @param date
 */
export function dateToDateString(date: Date): string {
    return `${ date.getDate() }.${ date.getMonth() + 1 }.${ date.getFullYear() }`;
}