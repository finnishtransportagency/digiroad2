/*jshint expr: true*/
define(['chai', 'eventbus', 'TestHelpers', 'AssetsTestData'], function(chai, eventbus, testHelpers, AssetsTestData) {
  var expect = chai.expect;

  describe('when loading application with two individual bus stops with same validity periods', function() {
    var openLayersMap;
    var testAsset1 = AssetsTestData.generateAsset({id: 1, nationalId: 1, lon: 374704.900011667, lat: 6677465.03054922, roadLinkId: 2148015});
    var testAsset2 = AssetsTestData.generateAsset({id: 2, nationalId: 2, lon: 374710.131074107, lat: 6677460.91654743, roadLinkId: 2148015});
    before(function(done) {
      var assetsData = [testAsset1, testAsset2];
      var backend = testHelpers.fakeBackend(assetsData, testAsset2, 12);
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        done();
      }, backend);
    });

    describe('and moving bus stop #2 near bus stop #1 and saving', function() {
      var originalMarker1Position;
      before(function() {
        var marker1 = _.find(testHelpers.getAssetMarkers(openLayersMap), {id: testAsset1.id});
        applicationModel.assetGroupingDistance = geometrycalculator.getSquaredDistanceBetweenPoints(testAsset1, {
          lon: testAsset2.lon - 1,
          lat: testAsset2.lat + 1
        });
        //TODO
        //originalMarker1Position = marker1.bounds;
        testHelpers.clickVisibleEditModeButton();
        testHelpers.clickMarker(testAsset2.id, openLayersMap);
        testHelpers.moveMarker(testAsset2.id, openLayersMap, -1, 1);
        $('button.save').click();
      });
      it('groups the bus stops', function() {
        expect($('.root').length).to.equal(1);
      });
      it('maintains the position of stop #1', function() {
        var marker1 = _.find(testHelpers.getAssetMarkers(openLayersMap), {id: testAsset1.id});
        //TODO
        //expect(marker1.bounds).to.deep.equal(originalMarker1Position);
      });
      it('lines the bus stops horizontally', function() {
        var marker1 = $('[data-asset-id='+testAsset1.id+']');
        var marker2 = $('[data-asset-id='+testAsset2.id+']');
        //TODO
        //expect(marker1.offset().left).to.equal(marker2.offset().left);
      });
    });
  });
});