/*jshint expr: true*/
define(['chai', 'eventbus', 'TestHelpers'], function(chai, eventbus, testHelpers) {
  var expect = chai.expect;

  describe('when loading application', function() {
    before(function(done) { testHelpers.restartApplication(
      function() { done(); });
    });

    describe('and creating a new asset', function() {
      before(function() {
        $('.edit-mode-btn').click();
        $('.action.add').click();
        eventbus.trigger('map:clicked', {x: 100, y: 100});
      });

      it('it shows new marker', function() {
        expect($('.expanded-bus-stop')).to.be.visible;
      });
    });
  });
});
