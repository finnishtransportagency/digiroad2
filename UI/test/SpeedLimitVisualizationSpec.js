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
    it('displays speed limits', function() {
      var speedLimitVectors = testHelpers.getSpeedLimitFeatures(openLayersMap);
      var limits = _.map(speedLimitVectors, function(vector) { return vector.attributes.limit; });
      expect(limits).to.have.length(2);
      expect(limits).to.have.members([40, 60]);
    });
  });

});