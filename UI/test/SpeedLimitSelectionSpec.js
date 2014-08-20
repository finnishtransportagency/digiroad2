/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  var speedLimitsData = SpeedLimitsTestData.generate(1);
  var speedLimit = _.first(speedLimitsData);

  var selectSpeedLimit = function(map, speedLimitId) {
    var control = _.first(map.getControlsBy('displayClass', 'olControlSelectFeature'));
    var feature = _.find(testHelpers.getSpeedLimitFeatures(map), function(feature) {
      return feature.attributes.id === speedLimitId;
    });
    control.select(feature);
  };

  describe('when loading application with speed limit data', function() {
    var openLayersMap;
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.speed-limits').click();
        done();
      }, testHelpers.defaultBackend().withSpeedLimitsData(speedLimitsData));
    });

    describe('and selecting speed limit', function() {
      before(function() {
        selectSpeedLimit(openLayersMap, speedLimit.id);
      });
      it('it displays speed limit segment ID in asset form', function() {
        expect($('#feature-attributes header')).to.have.text('Segmentin ID: 1123812');
      });

      describe('and zooming in', function() {
        before(function() {
          $('.pzbDiv-plus').click();
        });
        it('maintains speed limit selection', function() {
          expect($('#feature-attributes header')).to.have.text('Segmentin ID: 1123812');
        });
      });

      describe('and clicking outside the speed limit', function() {
        before(function() {
          var startPoint = _.first(speedLimit.points);
          testHelpers.clickMap(openLayersMap, startPoint.x - 10, startPoint.y);
        });
        it('deselects speed limit', function() {
          expect($('#feature-attributes header')).not.to.exist;
        });
      });
    });

    describe('and selecting speed limit', function() {
      before(function() {
        selectSpeedLimit(openLayersMap, speedLimit.id);
      });
      describe('and selecting assets layer', function() {
        before(function() {
          $('.panel-header').filter(':visible').filter(function (i, element) {return _.contains($(element).text(), 'Joukkoliikenteen pysäkit'); }).click();
        });
        describe('and reselecting speed limits layer', function() {
          before(function() {
            $('.speed-limits').click();
          });
          it('deselects speed limit', function() {
            expect($('#feature-attributes header')).not.to.exist;
          });
        });
      });
    });
  });
});