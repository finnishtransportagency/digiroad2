/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  var openLayersMap;
  var splitBackendCalls = [];

  var speedLimitSplitting = function(id, roadLinkId, splitMeasure, success, failure) {
    splitBackendCalls.push({
      id: id,
      roadLinkId: roadLinkId,
      splitMeasure: splitMeasure
    });
    success();
  };

  describe('when loading application in edit mode with speed limit data', function() {
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.speed-limits').click();
        testHelpers.clickVisibleEditModeButton();
        done();
      }, testHelpers.defaultBackend().withSpeedLimitSplitting(speedLimitSplitting));
    });

    describe('and selecting cut tool', function() {
      before(function() { $('.action.cut').click(); });

      describe('and cutting a speed limit', function() {
        before(function() {
          var pixel = openLayersMap.getPixelFromLonLat(new OpenLayers.LonLat(374945, 6677538));
          openLayersMap.events.triggerEvent('click',  {target: {}, srcElement: {}, xy: {x: pixel.x, y: pixel.y}});
        });

        describe('and saving split speed limit', function() {
          before(function() {
            $('.save.btn').click();
          });

          it('relays split speed limit to backend', function() {
            expect(splitBackendCalls).to.have.length(1);
            var backendCall = _.first(splitBackendCalls);
            expect(backendCall.id).to.equal(1123812);
            expect(backendCall.roadLinkId).to.equal(1);
            expect(backendCall.splitMeasure).to.be.closeTo(58.414, 0.5);
          });
        });
      });
    });
  });
});