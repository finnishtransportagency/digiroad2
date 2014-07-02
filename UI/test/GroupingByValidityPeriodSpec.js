/*jshint expr: true*/
define(['chai'], function(chai) {
  var expect = chai.expect;
  var assert = chai.assert;

  describe('when loading application with overlapping bus stops in different validity periods', function() {
    it('only includes bus stops in the selected validity period to the group', function() {
      var currentStop = $('[data-asset-id=300347]');
      var futureStop = $('[data-asset-id=300348]');

      expect(futureStop).to.be.hidden;
      expect(currentStop).to.be.visible;

      expect(currentStop.find('.bus-basic-marker')).to.have.class('root');

      $('#map-tools input[name=future]:first').click();

      expect(futureStop).to.be.visible;
      expect(currentStop).to.be.visible;

      var currentStopMarker = currentStop.find('.bus-basic-marker');
      var futureStopMarker = futureStop.find('.bus-basic-marker');

      expect(currentStopMarker).to.have.class('root');
      expect(futureStopMarker).not.to.have.class('root');
      assert(currentStopMarker.offset().top > futureStopMarker.offset().top, 'Future stop is above current stop');

      $('#map-tools input[name=current]:first').click();

      expect(futureStop).to.be.visible;
      expect(currentStop).to.be.hidden;

      expect(futureStop.find('.bus-basic-marker')).to.have.class('root');

    });
  });

});