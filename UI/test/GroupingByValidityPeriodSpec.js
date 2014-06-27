/*jshint expr: true*/
define(['chai'], function(chai) {
  var expect = chai.expect;

  describe('when loading application with overlapping bus stops in different validity periods', function() {
    it('only includes bus stops in the selected validity period to the group', function() {
      var currentStop = $('[data-asset-id=300347]');
      var futureStop = $('[data-asset-id=300348]');

      expect(futureStop).to.be.hidden;
      expect(currentStop).to.be.visible;

      expect(currentStop.find('.bus-basic-marker')).to.have.class('root');
      expect(futureStop.find('.bus-basic-marker')).not.to.have.class('root');
    });
  });

});