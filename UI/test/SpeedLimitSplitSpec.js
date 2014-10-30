/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  var openLayersMap;
  var splitBackendCalls = [];

  var speedLimitSplitting = function(id, roadLinkId, splitMeasure, limit, success, failure) {
    splitBackendCalls.push({
      id: id,
      roadLinkId: roadLinkId,
      splitMeasure: splitMeasure,
      limit: limit
    });
    success([{ id: id, limit: 50, speedLimitLinks: SpeedLimitsTestData.generate(1) }, { id: 123456, limit: limit, speedLimitLinks: SpeedLimitsTestData.generate(1) }]);
  };

  var speedLimitConstructor = function(id) {
    return {
      speedLimitLinks: [{
        roadLinkId: 5540,
        position: 0
      }]
    };
  };

  // todo: enable cut tool test once cutting is fixed for links with varying digitization directions
  xdescribe('when loading application in edit mode with speed limit data', function() {
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.speed-limits').click();
        testHelpers.clickVisibleEditModeButton();
        done();
      }, testHelpers.defaultBackend().withSpeedLimitSplitting(speedLimitSplitting).withSpeedLimitConstructor(speedLimitConstructor));
    });

    describe('and selecting cut tool', function() {
      before(function() { $('.action.cut').click(); });

      describe('and cutting a speed limit', function() {
        before(function() {
          var pixel = openLayersMap.getPixelFromLonLat(new OpenLayers.LonLat(374945, 6677538));
          openLayersMap.events.triggerEvent('click',  {target: {}, srcElement: {}, xy: {x: pixel.x, y: pixel.y}});
        });

        describe('and setting limit for new speed limit', function() {
          before(function() {
            $('select.speed-limit option[value="100"]').prop('selected', true).change();
          });

          describe('and saving split speed limit', function() {
            before(function() {
              $('.save.btn').click();
            });

            it('relays split speed limit to backend', function() {
              expect(splitBackendCalls).to.have.length(1);
              var backendCall = _.first(splitBackendCalls);
              expect(backendCall.id).to.equal(1123812);
              expect(backendCall.roadLinkId).to.equal(5540);
              expect(backendCall.splitMeasure).to.be.closeTo(58.414, 0.5);
              expect(backendCall.limit).to.equal(100);
            });
          });
        });

      });
    });
  });
});