/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  var lineStrings = function(features) {
    return _.filter(features, function(feature) {
      return feature.getGeometry() instanceof ol.geom.LineString;
    });
  };
  var points = function(features) {
    return _.filter(features, function(feature) {
      return feature.getGeometry() instanceof ol.geom.Point;
    });
  };

  describe('when loading application with speed limit data', function() {
    var openLayersMap;
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        testHelpers.selectLayer('speedLimit');
        done();
      });
    });
    it('displays speed limits', function() {
      var speedLimitVectors = lineStrings(testHelpers.getSpeedLimitFeatures(openLayersMap));
      var limits = _.map(speedLimitVectors, function(vector) { return vector.getProperties().value; });
      expect(limits).to.have.length(2);
      expect(limits).to.have.members([40, 60]);
    });
    it('displays speed limit signs', function() {
      var speedLimitSigns = points(testHelpers.getSpeedLimitFeatures(openLayersMap));
      var limits = _.map(speedLimitSigns, function(point) { return point.getProperties().value; });
      expect(limits).to.have.length(2);
      expect(limits).to.have.members([40, 60]);
    });
  });

});
