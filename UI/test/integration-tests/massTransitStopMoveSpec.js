/*jshint expr: true*/
define(['chai', 'eventbus', 'TestHelpers', 'AssetsTestData'], function(chai, eventbus, testHelpers, assetsTestData) {
  var expect = chai.expect;
  var assetsData = assetsTestData.withValidityPeriods(['current', 'current']);
  var assetData = _.merge({}, _.omit(assetsData[0], 'roadLinkId'), {propertyData: []});

  describe('when loading application with two bus stops', function() {
    this.timeout(1500000);
    var openLayersMap;
    before(function(done) {
      var backend = testHelpers.fakeBackend(assetsData, assetData);
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        done();
      }, backend);
    });
    describe('and moving bus stop', function() {
      var originalCoordinates;
      var newCoordinates;
      var testAssetId = 300348;
      before(function(done) {
        var marker = testHelpers.getAssetMarker(openLayersMap, testAssetId);
        originalCoordinates = [marker.data.lon, marker.data.lat];
        testHelpers.clickVisibleEditModeButton();
        eventbus.trigger('massTransitStop:movementPermission', true);
        newCoordinates = [marker.data.lon + 1 , marker.data.lat];
        testHelpers.moveMassTransitStop(openLayersMap, testAssetId, newCoordinates, function(){
          done();
        });
      });
      it('moves bus stop', function() {
        var marker = testHelpers.getAssetMarker(openLayersMap, testAssetId);
        expect(newCoordinates[1]).to.be.above(marker.geometry.getCoordinates()[1]);
      });
      describe('and canceling bus stop move', function() {
        before(function(done) {
          eventbus.once('asset:updateCancelled', function(){
            done();
          });
          $('button.cancel').click();
        });

        it('returns bus stop to original location', function() {
          var marker = testHelpers.getAssetMarker(openLayersMap, testAssetId);
          expect(marker.geometry.getCoordinates()).to.deep.equal(originalCoordinates);
        });
      });
    });
  });
});
