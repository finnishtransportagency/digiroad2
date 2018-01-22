/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;

  var assertSpeedLimitIsSelectedWithLimitValue = function(openLayersMap, speedLimitId, limitValue) {
    var features = _.filter(testHelpers.getSpeedLimitFeatures(openLayersMap), function(feature) {
      return feature.getProperties().id === speedLimitId;
    });
    expect(features.length).not.to.equal(0);
    _.each(features, function(feature) {
      expect(feature.getProperties().value).to.equal(limitValue);
    });
    expect($('#feature-attributes .speed-limit :selected')).to.contain(limitValue.toString());
  };

  describe('when loading application with speed limit data', function() {
    var speedLimitsData = _.flatten(SpeedLimitsTestData.generate(2));
    var speedLimit = speedLimitsData[1];
    var openLayersMap;
    before(function (done) {
      testHelpers.restartApplication(function (map) {
        openLayersMap = map;
        testHelpers.selectLayer('speedLimit');
        done();
      }, testHelpers.defaultBackend()
        .withSpeedLimitsData([speedLimitsData]));
    });

    describe('and selecting speed limit', function() {
      before(function() {
        testHelpers.selectSpeedLimit(openLayersMap, speedLimit.id);
      });
      it('shows the latest modification within selected speed limits', function() {
        var lastModifiedElement = _.find($('#feature-attributes .form-control-static.asset-log-info'), function(e) { return _.contains($(e).text(), 'Muokattu viimeksi'); });
        expect($(lastModifiedElement).text()).to.equal('Muokattu viimeksi: later 11.07.2015 13:30:00');
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

  describe('when loading application with speed limit data', function() {
    var speedLimitsData = SpeedLimitsTestData.generate(2);
    var speedLimit = speedLimitsData[0][0];
    var openLayersMap;
    var backend;
    before(function (done) {
      backend = testHelpers.defaultBackend(_.take(speedLimitsData, 1)).withSpeedLimitsData(speedLimitsData);
      testHelpers.restartApplication(function (map) {
        openLayersMap = map;
        testHelpers.selectLayer('speedLimit');
        done();
      }, backend);
    });

    describe('and selecting speed limit, and moving map with backend giving more speed limits to selected group', function() {
      before(function() {
        testHelpers.selectSpeedLimit(openLayersMap, speedLimit.id);
        backend.withSpeedLimitsData([_.flatten(speedLimitsData)]);
        eventbus.trigger('map:moved', { selectedLayer: "speedLimit", zoom: 10, bbox: [374061, 6676946, 375292, 6678247] } );
      });
      it("maintains two features on map, only one of which is selected", function() {
        var uniqueFeatures = _.unique(testHelpers.getSpeedLimitFeatures(openLayersMap), function(f) {
          return f.getProperties().id;
        });

        expect(uniqueFeatures).to.have.length(2);
        var uniqueFeaturesSelected = _.unique(testHelpers.getSelectedSpeedLimitFeatures(openLayersMap), function(f) {
          return f.getProperties().id;
        });
        expect(uniqueFeaturesSelected).to.have.length(1);
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

    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        testHelpers.selectLayer('speedLimit');
        testHelpers.clickVisibleEditModeButton();
        done();
      }, testHelpers.defaultBackend()
        .withSpeedLimitsData(speedLimitGroup));
    });

    describe('and changing value of speed limit', function() {
      before(function() {
        testHelpers.selectSpeedLimit(openLayersMap, speedLimitId);
        $('#feature-attributes .form-control.speed-limit').val('100').change();
      });
      it('should update all speed limit links on map', function() {
        var features = _.filter(testHelpers.getSelectedSpeedLimitFeatures(openLayersMap), function(feature) {
          return _.some(speedLimitsInGroup, {id: feature.getProperties().id});
        });

        expect(features).not.to.equal(0);
        _.each(features, function(feature) {
          expect(feature.getProperties().value).to.equal(100);
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
});
