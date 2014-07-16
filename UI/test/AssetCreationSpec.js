/*jshint expr: true*/
define(['chai', 'eventbus'], function(chai, eventbus) {
  var expect = chai.expect;

  describe('when loading application and creating a new asset', function() {
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
