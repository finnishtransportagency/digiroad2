const assert = require('chai').assert;
const {Utils} = require("../../dist/utils/utils");

describe('Utils', function() {
    it('Date to local date string', function() {
        const winterDay     = Utils.dateToDateString(new Date("2022-03-21T21:59:21.817Z"));
        const winterNextDay = Utils.dateToDateString(new Date("2022-03-21T22:00:00.000Z"));
        const summerDay     = Utils.dateToDateString(new Date("2022-06-21T20:59:21.817Z"));
        const summerNextDay = Utils.dateToDateString(new Date("2022-06-21T21:00:00.000Z"));

        assert.equal(winterDay, "21.3.2022");
        assert.equal(winterNextDay, "22.3.2022");
        assert.equal(summerDay, "21.6.2022");
        assert.equal(summerNextDay, "22.6.2022");
    });

    it('Check promise results returns results when all promises succeed', async function () {
        const succeedingPromises = [
            new Promise((resolve) => resolve("1")),
            new Promise((resolve) => resolve("2")),
            new Promise((resolve) => resolve("3"))
        ];
        const results = await Promise.allSettled(succeedingPromises);
        const extracted = Utils.checkResultsForErrors(results, "Test error");
        assert.equal(extracted.length, 3);
        assert.equal(extracted.includes("1"), true);
    });

    it('Check promise results throws error if one or more promises failed', async function () {
        const error = "Test error";
        const promises = [
            new Promise((resolve) => resolve("1")),
            new Promise((resolve) => resolve("2")),
            new Promise((resolve, reject) => reject()),
            new Promise((resolve) => resolve("3"))
        ];

        const results = await Promise.allSettled(promises);
        assert.throws(() => Utils.checkResultsForErrors(results, error), error);
    });
});