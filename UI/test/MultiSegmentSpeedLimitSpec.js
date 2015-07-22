/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;

  var assertSpeedLimitIsSelectedWithLimitValue = function(openLayersMap, speedLimitId, limitValue) {
    var features = _.filter(testHelpers.getSpeedLimitFeatures(openLayersMap), function(feature) {
      return feature.attributes.id === speedLimitId;
    });
    expect(features.length).not.to.equal(0);
    _.each(features, function(feature) {
      expect(feature.attributes.value).to.equal(limitValue);
    });
    expect($('#feature-attributes .speed-limit :selected')).to.have.text(limitValue.toString());
  };

  describe('when loading application with speed limit data', function() {
    var speedLimitsData = SpeedLimitsTestData.generate(1);
    var speedLimit = speedLimitsData[0][0];
    var openLayersMap;
    before(function (done) {
      testHelpers.restartApplication(function (map) {
        openLayersMap = map;
        $('.speed-limits').click();
        done();
      }, testHelpers.defaultBackend()
        .withSpeedLimitsData(speedLimitsData)
        .withSpeedLimitConstructor(SpeedLimitsTestData.generateSpeedLimitConstructor(speedLimitsData)));
    });

    describe('and selecting speed limit', function() {
      before(function() {
        testHelpers.selectSpeedLimit(openLayersMap, speedLimit.id);
      });

      describe('and clicking on the background map', function() {
        before(function() {
          var layer = _.find(openLayersMap.layers, function(layer) {
            return layer.isBaseLayer;
          }).div;
          testHelpers.clickElement(layer);
        });
        it('deselects speed limit', function() {
          expect($('#feature-attributes header')).not.to.exist;
        });
      });

      describe('and selecting assets layer', function() {
        before(function() {
          $('.panel-header').filter(':visible').filter(function(i, element) {
            return _.contains($(element).text(), 'Joukkoliikenteen pysäkit');
          }).click();
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

  describe('when loading application in edit mode with speed limits', function() {
    var openLayersMap;
    var speedLimitId = 13;
    var speedLimitGroup = _.filter(SpeedLimitsTestData.generate(), function(linkGroup) {
      return _.some(linkGroup, {id: speedLimitId});
    });
    var speedLimitsInGroup = _.flatten(speedLimitGroup);

    var speedLimitConstructor = SpeedLimitsTestData.generateSpeedLimitConstructor(speedLimitGroup);
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.speed-limits').click();
        testHelpers.clickVisibleEditModeButton();
        done();
      }, testHelpers.defaultBackend()
        .withSpeedLimitsData(speedLimitGroup)
        .withSpeedLimitConstructor(speedLimitConstructor)
        .withSpeedLimitUpdate(speedLimitConstructor(speedLimitId)));
    });

    describe('and changing value of speed limit', function() {
      before(function() {
        testHelpers.selectSpeedLimit(openLayersMap, speedLimitId);
        $('#feature-attributes .form-control.speed-limit').val('100').change();
      });
      it('should update all speed limit links on map', function() {
        var features = _.filter(testHelpers.getSpeedLimitFeatures(openLayersMap), function(feature) {
          return _.some(speedLimitsInGroup, {id: feature.attributes.id});
        });

        expect(features).not.to.equal(0);
        _.each(features, function(feature) {
          expect(feature.attributes.value).to.equal(100);
        });
      });

      describe('and cancelling the change', function() {
        before(function() {
          $('#feature-attributes .speed-limit button.cancel').click();
        });
        it('resets the update but maintains selection state', function() {
          assertSpeedLimitIsSelectedWithLimitValue(openLayersMap, speedLimitId, 60);
        });
      });
    });
  });

  describe('when loading application in edit mode with speed limits', function() {
    var openLayersMap;
    var speedLimitId = 13;
    var speedLimitGroup = _.filter(SpeedLimitsTestData.generate(), function(linkGroup) {
      return _.some(linkGroup, {id: speedLimitId});
    });
    var speedLimitsInGroup = _.flatten(speedLimitGroup);

    var speedLimitConstructor = SpeedLimitsTestData.generateSpeedLimitConstructor(speedLimitGroup);
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.speed-limits').click();
        testHelpers.clickVisibleEditModeButton();
        done();
      }, testHelpers.defaultBackend()
          .withSpeedLimitsData(speedLimitGroup)
          .withSpeedLimitConstructor(speedLimitConstructor)
          .withMultiSegmentSpeedLimitUpdate(speedLimitsInGroup));
    });

    describe('and changing value of speed limit, saving, changing it again, and cancelling', function() {
      before(function() {
        testHelpers.selectSpeedLimit(openLayersMap, speedLimitId);
        $('#feature-attributes .form-control.speed-limit').val('100').change();
        $('#feature-attributes button.save').click();

        $('#feature-attributes .form-control.speed-limit').val('40').change();
        $('#feature-attributes button.cancel').click();
      });

      it('resets back to saved speed limit value', function() {
        assertSpeedLimitIsSelectedWithLimitValue(openLayersMap, speedLimitId, 100);
      });
    });
  });
});