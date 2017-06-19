/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  var assert = chai.assert;

  describe('when loading application with overlapping bus stops in different validity periods', function() {
    var openLayersMap;
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        done();
      });
    });

    it('only includes bus stops in the selected validity period to the group', function() {

      var features = testHelpers.getMassTransitStopFeatures(openLayersMap);
      expect(features).to.have.length(1);
      expect(features[0].getProperties().data.id).to.equal(300347);

      expect(features[0].getProperties().data.group.assetGroup).to.have.length(2);

      var featureFromGroup = _.find(features[0].getProperties().data.group.assetGroup, function(feature){
          return feature.id === 300348;
      });
      expect([featureFromGroup]).to.have.length(1);
    });

    describe('and when selecting future validity period', function() {
      before(function() {
        $('#map-tools input[name=future]:first').click();
      });

      it('includes bus stops in future and current validity period', function() {

        var features = testHelpers.getMassTransitStopFeatures(openLayersMap);
        expect(features).to.have.length(2);
        expect(features[0].getProperties().data.id).to.equal(300347);
        expect(features[1].getProperties().data.id).to.equal(300348);

      });

      describe('and current validity period is deselected', function() {
        before(function() {
          $('#map-tools input[name=current]:first').click();
        });

        it('includes only bus stop in future', function() {

          var features = testHelpers.getMassTransitStopFeatures(openLayersMap);
          expect(features).to.have.length(1);
          expect(features[0].getProperties().data.id).to.equal(300348);

          expect(features[0].getProperties().data.group.assetGroup).to.have.length(2);

          var featureFromGroup = _.find(features[0].getProperties().data.group.assetGroup, function(feature){
            return feature.id === 300347;
          });
          expect([featureFromGroup]).to.have.length(1);
        });
      });
    });
  });
});