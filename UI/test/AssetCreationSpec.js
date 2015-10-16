/*jshint expr: true*/
define(['chai', 'eventbus', 'TestHelpers'], function(chai, eventbus, testHelpers) {
  var expect = chai.expect;

  describe('when loading application', function() {

    var openLayersMap;
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        testHelpers.clickVisibleEditModeButton();
        $('.action.add').click();
        done();
      }, undefined, 'massTransitStop');
    });

    describe('and creating a new asset', function() {
      before(function() {
        testHelpers.clickMap(openLayersMap, 100, 100);
      });

      it('it shows new marker', function() {
        expect($('.expanded-bus-stop')).to.be.visible;
      });
    });
  });
});
