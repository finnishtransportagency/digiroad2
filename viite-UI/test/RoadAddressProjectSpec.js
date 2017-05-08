/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  var assert = chai.assert;

  describe('when click on the Tieosoiteprojektit button', function() {
    this.timeout(1500000);
    var openLayersMap;

    before(function(done) { testHelpers.restartApplication(
      function(map) {
        openLayersMap = map;
        testHelpers.clickProjectListButton();
        done();
      });
    });

    it('open project list window', function () {
       assert($('#project-window:visible').length > 0, "Windows didn't open. Check permissions.");
    });

  });

});
