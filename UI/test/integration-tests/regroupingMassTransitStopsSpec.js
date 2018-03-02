/*jshint expr: true*/
define(['chai', 'eventbus', 'TestHelpers', 'AssetsTestData'], function(chai, eventbus, testHelpers, AssetsTestData) {
  var expect = chai.expect;

  describe('when loading application with two individual bus stops with same validity periods', function() {
    this.timeout(1500000);
    var openLayersMap;
    var testAsset1 = AssetsTestData.generateAsset({id: 1, nationalId: 1, lon: 374704.900011667, lat: 6677465.03054922, roadLinkId: 2148015});
    var testAsset2 = AssetsTestData.generateAsset({id: 2, nationalId: 2, lon: 374710.131074107, lat: 6677460.91654743, roadLinkId: 2148015});
    before(function(done) {
      var assetsData = [testAsset1, testAsset2];
      var backend = testHelpers.fakeBackend(assetsData, testAsset2, 12);
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        testHelpers.selectLayer('massTransitStop');
        testHelpers.clickVisibleEditModeButton();
        done();
      }, backend);
    });

    describe('and moving bus stop #2 near bus stop #1 and saving', function() {
      var originalMarker1Position;
      before(function(done) {
        var marker1 = testHelpers.getAssetMarker(openLayersMap, testAsset1.id);
        originalMarker1Position = marker1.geometry;
        var coordinate = [testAsset2.lon - 4, testAsset2.lat + 5];
        testHelpers.moveMassTransitStop(openLayersMap, testAsset2.id, coordinate, function(){
          $('button.save').click();
          done();
        });
      });
      it('maintains the position of stop #1', function() {
        var marker1 = testHelpers.getAssetMarker(openLayersMap, testAsset1.id);
        expect(marker1.geometry).to.deep.equal(originalMarker1Position);
      });
      it('lines at the same position', function() {
        var marker1 = testHelpers.getAssetMarker(openLayersMap, testAsset1.id);
        var marker2 = testHelpers.getAssetMarker(openLayersMap, testAsset2.id);
        expect([marker1.data.group.lon, marker1.data.group.lat]).to.deep.equal([marker2.data.group.lon, marker2.data.group.lat]);
      });
    });
  });
});