export const Utils = {
    /**
     * Transforms date to string DD.MM.YYYY in Helsinki time
     */
    dateToDateString(date: Date): string {
        return date.toLocaleDateString('fi-FI', {timeZone: 'Europe/Helsinki'});
    },

    /**
     * Checks if any promise contains an error. If no error is found returns results.
     */
    checkResultsForErrors(results: PromiseSettledResult<any>[], errorMsg: string): any[] {
        const errors = results.filter(({ status }) => status === 'rejected') as PromiseRejectedResult[];
        if (errors.length > 0) {
            errors.forEach((error) => console.error(error.reason) );
            throw new Error(errorMsg);
        }
        const fulfilled = results as PromiseFulfilledResult<any>[];
        return fulfilled.map(result => result.value);
    }
}