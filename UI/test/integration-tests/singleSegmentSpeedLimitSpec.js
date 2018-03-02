/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  var speedLimitsData = SpeedLimitsTestData.generate(1);
  var speedLimit = _.first(_.flatten(speedLimitsData));

  var assertSpeedLimitIsSelectedWithLimitValue = function(openLayersMap, speedLimitId, limitValue) {
    var features = _.filter(testHelpers.getSpeedLimitFeatures(openLayersMap), function(feature) {
      return feature.getProperties().id === speedLimitId;
    });
    expect(features.length).not.to.equal(0);
    _.each(features, function(feature) {
      expect(feature.getProperties().value).to.equal(limitValue);
    });
    expect($('#feature-attributes .speed-limit :selected')).to.contain(limitValue.toString());
    expect($('#feature-attributes header span')).to.have.text("Segmentin ID: " + speedLimitId);
  };

  describe('when loading application with speed limit data', function() {
    this.timeout(1500000);
    var openLayersMap;
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        testHelpers.selectLayer('speedLimit');
        done();
      }, testHelpers.defaultBackend()
        .withSpeedLimitsData(speedLimitsData));
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
          eventbus.once('speedLimit:unselect', function() { done(); });

          var interaction = _.find(openLayersMap.getInteractions().getArray(), function(interaction) {
            return interaction.get('name') === 'speedLimit';
          });

          interaction.getFeatures().clear();
          interaction.dispatchEvent({
            type: 'select',
            selected: [],
            deselected: []
          });
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
          testHelpers.selectLayer('massTransitStop');
        });
        describe('and reselecting speed limits layer', function() {
          before(function() {
            testHelpers.selectLayer('speedLimit');
          });
          it('deselects speed limit', function() {
            expect($('#feature-attributes header')).not.to.exist;
          });
        });
      });
    });
  });

  describe('when loading application in edit mode with speed limits', function() {
    this.timeout(1500000);
    var openLayersMap;
    var speedLimitId = 13;
    var speedLimits = [_.find(SpeedLimitsTestData.generate(), function(g) { return _.some(g, {id: speedLimitId}); })];

    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        testHelpers.selectLayer('speedLimit');
        testHelpers.clickVisibleEditModeButton();
        done();
      }, testHelpers.defaultBackend()
        .withSpeedLimitsData(speedLimits));
    });

    describe('and changing value of speed limit', function() {
      before(function() {
        testHelpers.selectSpeedLimit(openLayersMap, speedLimitId, true);
        $('#feature-attributes .form-control.speed-limit').val('100').change();
      });
      it('should update all speed limit links on map', function() {
        var features = _.filter(testHelpers.getSelectedSpeedLimitFeatures(openLayersMap), function(feature) {
          return feature.getProperties().id === speedLimitId;
        });
        expect(features.length).not.to.equal(0);
        _.each(features, function(feature) {
          expect(feature.getProperties().value).to.equal(100);
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

  describe('when loading application in edit mode with speed limits', function() {
    this.timeout(1500000);
    var openLayersMap;
    var speedLimitId = 13;
    var speedLimits = [_.find(SpeedLimitsTestData.generate(), function(g) { return _.some(g, {id: speedLimitId}); })];
    var modificationData = {
      13: {modifiedBy: 'modifier', modifiedAt: '10.09.2014 13:36:58', createdBy: 'creator', createdAt: '10.09.2014 13:36:57'},
      14: {modifiedBy: null, modifiedAt: null, createdBy: null, createdAt: null}
    };

    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        testHelpers.selectLayer('speedLimit');
        testHelpers.clickVisibleEditModeButton();
        done();
      }, testHelpers.defaultBackend()
        .withSpeedLimitsData(speedLimits));
    });

    describe('and changing value of speed limit', function() {
      before(function() {
        testHelpers.selectSpeedLimit(openLayersMap, speedLimitId, true);
        $('#feature-attributes .form-control.speed-limit').val('100').change();
      });

      describe('and saving the change', function() {
        before(function() {
          _.merge(speedLimits[0][0], modificationData[13]);
          $('#feature-attributes button.save').click();
        });
        it('it updates the modified and created fields', function() {
          testHelpers.selectSpeedLimit(openLayersMap, speedLimitId, true);
          expect($('#feature-attributes .asset-log-info:first')).to.have.text('Lisätty järjestelmään: creator 10.09.2014 13:36:57');
          var lastModifiedElement = _.find($('#feature-attributes .form-control-static.asset-log-info'), function(e) { return _.contains($(e).text(), 'Muokattu viimeksi'); });
          expect($(lastModifiedElement).text()).to.equal('Muokattu viimeksi: modifier 10.09.2014 13:36:58');
        });
      });
    });
  });
});
