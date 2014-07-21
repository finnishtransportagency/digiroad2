/*jshint expr: true*/
define(['chai', 'eventbus', 'TestHelpers', 'AssetsTestData'], function(chai, eventbus, testHelpers, assetsTestData) {
  var expect = chai.expect;
  var assetsData = assetsTestData.withValidityPeriods(['current', 'current']);
  var assetData = _.merge({}, assetsData[0], {propertyData: []});

  describe('when loading application with two bus stops', function() {
    var openLayersMap;
    before(function(done) {
      var backend = testHelpers.fakeBackend(assetsData, assetData);
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        done();
      }, backend);
    });
    describe('and moving bus stop', function() {
      var originalYPosition;
      var testAssetId = 300348;
      before(function(done) {
        var marker = _.find(testHelpers.getAssetMarkers(openLayersMap), {id: testAssetId});
        originalYPosition = marker.bounds.top;
        $('.edit-mode-btn').click();
        eventbus.once('asset:fetched', function() {
          testHelpers.moveMarker(testAssetId, openLayersMap, 1, 0);
          done();
        });
        testHelpers.clickMarker(testAssetId, openLayersMap);
      });
      it('moves bus stop', function() {
        var marker = _.find(testHelpers.getAssetMarkers(openLayersMap), function(marker) {
          return marker.id === testAssetId && marker.bounds.top > originalYPosition;
        });
        expect(marker).not.to.be.undefined;
      });
      describe('and canceling bus stop move', function() {
        before(function() { $('button.cancel').click(); });

        it('returns bus stop to original location', function() {
          var markerInOriginalYPosition = _.all(_.where(testHelpers.getAssetMarkers(openLayersMap), {id: testAssetId}), function(marker) {
            return marker.bounds.top === originalYPosition;
          });
          expect(markerInOriginalYPosition).to.be.true;
        });
      });
    });
  });
});
