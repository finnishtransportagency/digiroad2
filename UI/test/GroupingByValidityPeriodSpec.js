/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  var assert = chai.assert;

  describe('when loading application with overlapping bus stops in different validity periods', function() {
    before(function(done) { testHelpers.restartApplication(
      function() { done(); });
    });

    it('only includes bus stops in the selected validity period to the group', function() {
      var currentStop = $('[data-asset-id=300347]');
      var futureStop = $('[data-asset-id=300348]');

      expect(futureStop).to.be.hidden;
      expect(currentStop).to.be.visible;

      expect(currentStop.find('.bus-basic-marker')).to.have.class('root');
    });

    describe('and when selecting future validity period', function() {
      before(function() {
        $('#map-tools input[name=future]:first').click();
      });

      it('includes bus stops in future and current validity period', function() {
        var currentStop = $('[data-asset-id=300347]');
        var futureStop = $('[data-asset-id=300348]');

        expect(futureStop).to.be.visible;
        expect(currentStop).to.be.visible;

        var currentStopMarker = currentStop.find('.bus-basic-marker');
        var futureStopMarker = futureStop.find('.bus-basic-marker');

        expect(currentStopMarker).to.have.class('root');
        expect(futureStopMarker).not.to.have.class('root');
        assert(currentStopMarker.offset().top > futureStopMarker.offset().top, 'Future stop is above current stop');
      });

      describe('and current validity period is deselected', function() {
        before(function() {
          $('#map-tools input[name=current]:first').click();
        });

        it('includes only bus stop in future', function() {
          var currentStop = $('[data-asset-id=300347]');
          var futureStop = $('[data-asset-id=300348]');

          expect(futureStop).to.be.visible;
          expect(currentStop).to.be.hidden;

          expect(futureStop.find('.bus-basic-marker')).to.have.class('root');
        });
      });
    });
  });
});