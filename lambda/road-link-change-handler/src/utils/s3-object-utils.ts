/**
 * S3 Change set naming convention:
 * since_until.json i.e. 2022-7-1_2022-7-31.json
 */
export const S3ObjectUtils = {
    createKey(since: string, until: string): string {
        function formatDate(date: string): string {
            let [day, month, year] = date.split(".");
            if (!day || !month || !year) {
                throw new Error("Invalid dates provided. Cannot create name for S3 object.");
            }
            day = day.length == 1 ? '0' + day : day;
            month = month.length == 1 ? '0' + month : month;
            return `${year}-${month}-${day}`;
        }
        return `${formatDate(since)}_${formatDate(until)}.json`;
    },

    /**
     * Returns valid s3 keys sorted in descending order by until date
     */
    sortKeysByUntilDate(keys: (string | undefined)[]): string[] {
        const objectKeys = keys.filter(this.isValidKey) as string[];
        return objectKeys.sort((a, b) => {
            return this.extractUntilDateFromKey(b).getTime() - this.extractUntilDateFromKey(a).getTime();
        });
    },

    extractUntilDateFromKey(objectKey: string): Date {
        if (!this.isValidKey(objectKey)) throw new Error(`${objectKey} is not valid change set key.`);
        const dates = objectKey.replace(".json", "");
        const [, until] = dates.split("_");
        return new Date(until);
    },

    isValidKey(objectKey?: string): Boolean {
        if (!objectKey) return false;
        const dates = objectKey.replace(".json", "");
        const [since, until] = dates.split("_");
        return !isNaN(Date.parse(since)) && !isNaN(Date.parse(until));
    }
}