/*jshint expr: true*/
define(['chai', 'eventbus', 'TestHelpers'], function(chai, eventbus, testHelpers) {
  var expect = chai.expect;

  describe('when loading application', function() {

    var openLayersMap;
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.edit-mode-btn').click();
        $('.action.add').click();
        done();
      });
    });

    describe('and creating a new asset', function() {
      before(function(done) {
        openLayersMap.events.triggerEvent('click',  {target: {}, srcElement: {}, xy: {x: 100, y: 100}});
        eventbus.once('map:clicked', function() {
          done();
        });

      });

      it('it shows new marker', function() {
        expect($('.expanded-bus-stop')).to.be.visible;
      });
    });
  });
});
