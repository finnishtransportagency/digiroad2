/*jshint expr: true*/
define(['chai', 'eventbus', 'TestHelpers', 'AssetsTestData'], function(chai, eventbus, testHelpers, AssetsTestData) {
  var expect = chai.expect;

  describe('when loading application with bus stop', function() {
    var openLayersMap;
    var testAsset1 = AssetsTestData.generateAsset({id: 1, nationalId: 1, lon: 374704.900011667, lat: 6677465.03054922, roadLinkId: 2148015});
    before(function(done) {
      var assetsData = [testAsset1];
      var backend = testHelpers
        .fakeBackend(assetsData, {}, 12)
        .withAssetCreationTransformation(function(assetData) {
          return _.merge({}, assetData, {stopTypes: [2], id: 2});
        });
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        done();
      }, backend);
    });

    describe('and creating bus stop near bus stop and saving', function() {
      before(function() {
        testHelpers.clickVisibleEditModeButton();
        $('.action.add').click();
        testHelpers.clickMap(openLayersMap, testAsset1.lon, testAsset1.lat + 4.0);
        $('button.save').click();
      });
      it('groups the bus stops', function() {
        expect($('.root').length).to.equal(1);
      });
      it('lines the bus stops horizontally', function() {
        var marker1 = $('[data-asset-id='+testAsset1.id+']');
        var marker2 = $('[data-asset-id=2]');
        //TODO
        //expect(marker1.offset().left).to.equal(marker2.offset().left);
      });
    });
  });
});