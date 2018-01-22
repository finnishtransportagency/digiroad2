/*jshint expr: true*/
define(['chai', 'eventbus', 'TestHelpers'], function(chai, eventbus, testHelpers) {
  var expect = chai.expect;

  describe('when loading application', function() {
    this.timeout(1500000);
    var openLayersMap;
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        testHelpers.clickVisibleEditModeButton();
        $('.action.add').click();
        done();
      });
    });

    describe('and creating a new asset', function() {
      before(function() {
        testHelpers.clickMap(openLayersMap, 100, 100);
      });

      it('it shows new marker', function() {
        var features = testHelpers.getSelectedAssetMarkers(openLayersMap);
        expect(features).to.have.length(1);
      });
    });
  });
});
