/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;

  describe('when loading application with speed limit data and selecting speed limits', function() {
    var openLayersMap;
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.speed-limits').click();
        done();
      });
    });
    it('displays speed limit', function() {
      var speedLimitVectors = testHelpers.getSpeedLimitFeatures(openLayersMap);
      expect(speedLimitVectors.length).to.equal(1);
    });
  });

});