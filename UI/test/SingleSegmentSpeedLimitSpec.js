/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  var speedLimitsData = SpeedLimitsTestData.generate(1);
  var speedLimit = _.first(_.flatten(speedLimitsData));

  var speedLimitConstructor = function(ids) {
    return _.map(ids, function(id) {
      return {
        id: id,
        modifiedBy: 'modifier',
        createdBy: 'creator',
        speedLimitLinks: [_.find(_.flatten(speedLimitsData), { id: id })]
      };
    });
  };

  var assertSpeedLimitIsSelectedWithLimitValue = function(openLayersMap, speedLimitId, limitValue) {
    var features = _.filter(testHelpers.getSpeedLimitFeatures(openLayersMap), function(feature) {
      return feature.attributes.id === speedLimitId;
    });
    expect(features.length).not.to.equal(0);
    _.each(features, function(feature) {
      expect(feature.attributes.value).to.equal(limitValue);
    });
    expect($('#feature-attributes .speed-limit :selected')).to.have.text(limitValue.toString());
    expect($('#feature-attributes header span')).to.have.text("Segmentin ID: " + speedLimitId);
  };

  describe('when loading application with speed limit data', function() {
    var openLayersMap;
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.speed-limits').click();
        done();
      }, testHelpers.defaultBackend()
        .withSpeedLimitsData(speedLimitsData)
        .withSpeedLimitConstructor(speedLimitConstructor));
    });

    describe('and selecting speed limit', function() {
      before(function() {
        testHelpers.selectSpeedLimit(openLayersMap, speedLimit.id, true);
      });
      it('it displays speed limit segment ID in asset form', function() {
        expect($('#feature-attributes header span')).to.have.text('Segmentin ID: 1123812');
      });
      it('it displays speed limit creator', function() {
        expect($('#feature-attributes .asset-log-info:first')).to.have.text('Lisätty järjestelmään: creator');
      });
      it('it displays speed limit modifier', function() {
        var lastModifiedElement = _.find($('#feature-attributes .form-control-static.asset-log-info'), function(e) { return _.contains($(e).text(), 'Muokattu viimeksi'); });
        expect($(lastModifiedElement).text()).to.equal('Muokattu viimeksi: modifier');
      });

      describe('and zooming in', function() {
        before(function() {
          $('.pzbDiv-plus').click();
        });
        it('maintains speed limit selection', function() {
          expect($('#feature-attributes header span')).to.have.text('Segmentin ID: 1123812');
        });
      });

      describe('and clicking on the background map', function() {
        before(function(done) {
          var layer = _.find(openLayersMap.layers, function(layer) { return layer.isBaseLayer; }).div;
          eventbus.once('speedLimit:unselect', function() { done(); });
          testHelpers.clickElement(layer);
        });
        it('deselects speed limit', function() {
          expect($('#feature-attributes header')).not.to.exist;
        });
      });
    });

    describe('and selecting speed limit', function() {
      before(function() {
        testHelpers.selectSpeedLimit(openLayersMap, speedLimit.id, true);
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

  xdescribe('when loading application in edit mode with speed limits', function() {
    var openLayersMap;
    var speedLimitId = 13;
    var speedLimits = _.filter(SpeedLimitsTestData.generate(), function(link) { return link.id === speedLimitId; });

    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.speed-limits').click();
        testHelpers.clickVisibleEditModeButton();
        done();
      }, testHelpers.defaultBackend().withSpeedLimitsData(speedLimits).withSpeedLimitConstructor(SpeedLimitsTestData.generateSpeedLimitConstructor(speedLimits)));
    });

    describe('and changing value of speed limit', function() {
      before(function() {
        testHelpers.selectSpeedLimit(openLayersMap, speedLimitId, true);
        $('#feature-attributes .form-control.speed-limit').val('100').change();
      });
      it('should update all speed limit links on map', function() {
        var features = _.filter(testHelpers.getSpeedLimitFeatures(openLayersMap), function(feature) {
          return feature.attributes.id === speedLimitId;
        });
        expect(features.length).not.to.equal(0);
        _.each(features, function(feature) {
          expect(feature.attributes.value).to.equal(100);
        });
      });

      describe('and cancelling the change', function() {
        before(function() {
          $('#feature-attributes .speed-limit button.cancel').click();
        });
        it('resets the update but maintains selection state', function() { assertSpeedLimitIsSelectedWithLimitValue(openLayersMap, speedLimitId, 60); });
      });
    });
  });

  xdescribe('when loading application in edit mode with speed limits', function() {
    var openLayersMap;
    var speedLimitId = 13;
    var speedLimits = _.filter(SpeedLimitsTestData.generate(), function(link) {
      return link.id === speedLimitId;
    });
    var speedLimitConstructor = SpeedLimitsTestData.generateSpeedLimitConstructor(speedLimits);

    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.speed-limits').click();
        testHelpers.clickVisibleEditModeButton();
        done();
      }, testHelpers.defaultBackend()
        .withSpeedLimitsData(speedLimits)
        .withSpeedLimitConstructor(speedLimitConstructor)
        .withSpeedLimitUpdate(_.merge(speedLimitConstructor(13), {modifiedBy: 'modifier', modifiedDateTime: '10.09.2014 13:36:58', createdBy: 'creator', createdDateTime: '10.09.2014 13:36:57'})));
    });

    describe('and changing value of speed limit', function() {
      before(function() {
        testHelpers.selectSpeedLimit(openLayersMap, speedLimitId, true);
        $('#feature-attributes .form-control.speed-limit').val('100').change();
      });

      describe('and saving the change', function() {
        before(function() {
          $('#feature-attributes button.save').click();
        });
        it('it updates the modified and created fields', function() {
          expect($('#feature-attributes .asset-log-info:first')).to.have.text('Lisätty järjestelmään: creator 10.09.2014 13:36:57');
          expect($('#feature-attributes .asset-log-info:last')).to.have.text('Muokattu viimeksi: modifier 10.09.2014 13:36:58');
        });
      });

      describe('and changing value of speed limit back to original', function() {
        before(function() {
          $('#feature-attributes .form-control.speed-limit').val('60').change();
        });

        describe('and cancelling the change', function() {
          before(function() {
            $('#feature-attributes button.cancel').click();
          });

          it('resets back to saved speed limit value', function() { assertSpeedLimitIsSelectedWithLimitValue(openLayersMap, speedLimitId, 100); });
        });
      });
    });
  });
});
