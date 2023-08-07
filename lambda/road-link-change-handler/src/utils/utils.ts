export class Utils {
    
    Utils () {}
    
    /**
     * Transforms date to string DD.MM.YYYY in Helsinki time
     */
    static dateToDateString(date: Date): string {
        return date.toLocaleDateString('fi-FI', {timeZone: 'Europe/Helsinki'});
    }

    static sleep(ms:number):Promise<void> { return new Promise(resolve => setTimeout(resolve, ms)); }
    /**
     * Checks if any promise contains an error. If no error is found returns results.
     */
    static checkResultsForErrors(results: PromiseSettledResult<any>[], errorMsg: string): any[] {
        this.checkError(results,errorMsg);
        return (results as PromiseFulfilledResult<any>[]).map(result => result.value);
    }

    static checkError(results: PromiseSettledResult<any>[], errorMsg: string) {
        const errors = results.filter(({status}) => status === 'rejected') as PromiseRejectedResult[];
        if (errors.length > 0) {
            errors.forEach((error) => console.error(error.reason));
            throw new Error(errorMsg);
        }
    }
    /**
     * Checks if any promise contains an error. If no error is found returns results.
     */
    static checkResultsForErrorsNestedPromise(results:  PromiseSettledResult<PromiseSettledResult<any>[]>[], errorMsg: string): any[] {
        this.checkError(results,errorMsg);  // check outerPromise
        const innerPromise = (results as PromiseFulfilledResult<PromiseSettledResult<any>[]>[]).flatMap(t=>t.value);
        this.checkError(innerPromise,errorMsg); // check innerPromise
        return (innerPromise as PromiseFulfilledResult<any>[]).map(result => result.value);
    }
}