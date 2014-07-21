/*jshint expr: true*/
define(['chai', 'TestHelpers', 'AssetsTestData'], function(chai, testHelpers, assetsTestData) {
  var expect = chai.expect;
  var assert = chai.assert;
  var assetsData = assetsTestData.withValidityPeriods(['current', 'current']);
  var assetData = _.merge({}, assetsData[0], {propertyData: []});

  describe('when loading application with two bus stops', function() {
    var openLayersMap;
    before(function(done) {
      var backend = testHelpers.fakeBackend(assetsData, assetData);
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        done();
      }, backend);
    });
    describe('and moving bus stop', function() {
      describe('and canceling bus stop move', function() {
        it('returns bus stop to original location', function() {
        });
      });
    });
  });
});