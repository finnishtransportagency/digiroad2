const assert = require('chai').assert;

const {S3ObjectUtils} = require('../../dist/utils/s3-object-utils');

describe('S3 service', function() {
    it('Create s3 object name from valid dates', function () {
        const oneDigitSince = '1.2.2023';
        const twoDigitUntil = '13.12.2023';
        const changeSetName = S3ObjectUtils.createKey(oneDigitSince, twoDigitUntil);
        assert.equal(changeSetName, '2023-02-01_2023-12-13.json');
    });

    it('Creating name with invalid dates thrown an Error', function () {
        const [since, until] = ["invalid", "invalid"];
        assert.throws(() => S3ObjectUtils.createKey(since, until), 'Invalid dates provided. Cannot create name for S3 object.');
    });

    it('Sort change sets by until date', function () {
        const [invalidKey1, invalidKey2, invalidKey3] = ["invalid", "2022-03-13_2022-04-50.json", undefined];
        const keys = [
            "2023-02-15_2023-02-28.json", invalidKey1, "2023-03-13_2023-04-05.json", invalidKey2,
            "2023-03-01_2023-03-12.json", "2022-02-15_2022-02-28.json", "2023-02-01_2023-02-15.json", invalidKey3
        ];
        const sorted = S3ObjectUtils.sortKeysByUntilDate(keys);
        assert.equal(sorted.includes(invalidKey1), false);
        assert.equal(sorted.includes(invalidKey2), false);
        assert.equal(sorted.includes(invalidKey3), false);
        assert.equal(sorted[0], "2023-03-13_2023-04-05.json");

        const sortedEmpty = S3ObjectUtils.sortKeysByUntilDate([]);
        assert.equal(sortedEmpty.length, 0);

        const sortedInvalidKeys = S3ObjectUtils.sortKeysByUntilDate([invalidKey1, invalidKey2, invalidKey1]);
        assert.equal(sortedInvalidKeys.length, 0);
    });

    it('Extract until date from valid s3 key', function () {
        const key1 = "2023-03-13_2023-04-05.json";
        const date1 = S3ObjectUtils.extractUntilDateFromKey(key1);
        assert.equal(date1.toString(), new Date("2023-04-05").toString());

        const key2 = "2023-03-13_2023-04-05";
        const date2 = S3ObjectUtils.extractUntilDateFromKey(key2);
        assert.equal(date2.toString(), new Date("2023-04-05").toString());
    });

    it('Extracting until date from invalid s3 key throws error', function () {
        const key1 = "test.json";
        const key2 = "2023-03-13_test.json";
        assert.throws(() => S3ObjectUtils.extractUntilDateFromKey(key1), `${key1} is not valid change set key.`);
        assert.throws(() => S3ObjectUtils.extractUntilDateFromKey(key2), `${key2} is not valid change set key.`);
    });

    it('Testing if s3 key is valid', function () {
        const valid = "2023-03-13_2023-04-05.json";
        const validWithoutExtension = "2023-03-13_2023-04-05";
        const invalidDate = "2023-03-13_2023-04-55.json";
        const invalidKey = "test.json";
        const noKey = undefined;

        assert.equal(S3ObjectUtils.isValidKey(valid), true);
        assert.equal(S3ObjectUtils.isValidKey(validWithoutExtension), true);

        assert.equal(S3ObjectUtils.isValidKey(invalidDate), false);
        assert.equal(S3ObjectUtils.isValidKey(invalidKey), false);
        assert.equal(S3ObjectUtils.isValidKey(noKey), false);
    });
});
