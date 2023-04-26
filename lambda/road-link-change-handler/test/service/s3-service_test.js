const sinon = require('sinon');
const chai = require('chai').use(require('chai-as-promised'));
const assert = chai.assert;

const {S3Service} = require('../../dist/service/s3-service');

// Non-existent bucket used so that S3Service constructor won't throw error
process.env.CHANGE_BUCKET = "test";

describe('S3 service', function() {
    // Set up test service
    const testService = new S3Service();

    afterEach(function() {
        sinon.restore();
    });

    it('Use dates defined in the event', async function() {
        const testEvent = { since: "2022-10-12", until: "2022-10-24" };
        const [since, until] = await testService.getChangeTimeframe(testEvent);
        assert.equal(since, "12.10.2022");
        assert.equal(until, "24.10.2022");
    });

    it('Same dates for since and until', async function() {
        const testEvent = { since: "2022-10-12", until: "2022-10-12" };
        const [since, until] = await testService.getChangeTimeframe(testEvent);
        assert.equal(since, "12.10.2022");
        assert.equal(until, "12.10.2022");
    });

    it('Invalid dates provided in the event throws error', async function() {
        const testEvent = { since: "2022-10-24", until: "2022-10-12" };
        const expectedError = `Invalid dates provided (since: ${testEvent.since}, until: ${testEvent.until})`;
        await assert.isRejected(testService.getChangeTimeframe(testEvent), expectedError);
    });

    it('No dates provided in the event and no S3 change sets', async function() {
        const lastChanges = new Date("2022-10-13");
        sinon.stub(testService, 'getTimeOfLastFetchedChanges').returns(lastChanges);
        const [since,] = await testService.getChangeTimeframe({});
        assert.equal(since, "14.10.2022");
    });

    it('No dates provided in the event and no S3 change sets', async function() {
        sinon.stub(testService, 'getTimeOfLastFetchedChanges').returns(undefined);
        const [since,] = await testService.getChangeTimeframe({});
        assert.equal(since, "11.5.2022");
    });
});