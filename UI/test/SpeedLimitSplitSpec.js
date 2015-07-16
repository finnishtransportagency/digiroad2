/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  var openLayersMap;
  var splitBackendCalls = [];

  var speedLimitTestData = SpeedLimitSplitTestData.generateSpeedLimitLinks();
  var roadLinkTestData = SpeedLimitSplitTestData.generateRoadLinks();

  var speedLimitSplitting = function(id, mmlId, splitMeasure, value, success, failure) {
    splitBackendCalls.push({
      id: id,
      mmlId: mmlId,
      splitMeasure: splitMeasure,
      value: value
    });
    success([{ id: id, value: 50, speedLimitLinks: [speedLimitTestData[0]]}, { id: 123456, value: value, speedLimitLinks: [speedLimitTestData[0]] }]);
  };

  var speedLimitConstructor = function(id) {
    return {
      speedLimitLinks: _.filter(speedLimitTestData, function(limit) { return limit.id == id; })
    };
  };

  xdescribe('when loading application in edit mode with speed limit data', function() {
    before(function (done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.speed-limits').click();
        testHelpers.clickVisibleEditModeButton();
        done();
      }, testHelpers.defaultBackend()
          .withStartupParameters({ lon: 0.0, lat: 0.0, zoom: 11 })
          .withRoadLinkData(roadLinkTestData)
          .withSpeedLimitsData(speedLimitTestData)
          .withSpeedLimitSplitting(speedLimitSplitting)
          .withSpeedLimitConstructor(speedLimitConstructor));
    });

    describe('and selecting cut tool', function() {
      before(function () {
        $('.speed-limits .action.cut').click();
      });

      describe('and cutting a speed limit', function() {
        before(function () {
          var pixel = openLayersMap.getPixelFromLonLat(new OpenLayers.LonLat(0.0, 120.0));
          openLayersMap.events.triggerEvent('click', {target: {}, srcElement: {}, xy: {x: pixel.x, y: pixel.y}});
        });

        it('the speed limit should be split into two features', function() {
          var existingLimitPoints = testHelpers.getSpeedLimitVertices(openLayersMap, 111);
          var newLimitPoints = testHelpers.getSpeedLimitVertices(openLayersMap, null);

          existingLimitPoints = _.sortBy(existingLimitPoints, 'y');
          newLimitPoints = _.sortBy(newLimitPoints, 'y');

          expect(_.first(existingLimitPoints).y).to.equal(0);
          expect(_.last(existingLimitPoints).y).to.be.closeTo(120.0, 0.5);
          expect(_.first(newLimitPoints).y).to.be.closeTo(120.0, 0.5);
          expect(_.last(newLimitPoints).y).to.equal(200);
        });

        describe('and setting limit for new speed limit', function() {
          before(function() {
            $('select.speed-limit option[value="100"]').prop('selected', true).change();
          });

          describe('and saving split speed limit', function() {
            before(function() {
              $('.speed-limit .save.btn').click();
            });

            it('relays split speed limit to backend', function() {
              expect(splitBackendCalls).to.have.length(1);
              var backendCall = _.first(splitBackendCalls);
              expect(backendCall.id).to.equal(111);
              expect(backendCall.mmlId).to.equal(666);
              expect(backendCall.splitMeasure).to.be.closeTo(20.0, 0.5);
              expect(backendCall.value).to.equal(100);
            });
          });
        });
      });
    });
  });
});
