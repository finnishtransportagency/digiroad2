/*jshint expr: true*/
define(['chai', 'eventbus', 'TestHelpers'], function(chai, eventbus, testHelpers) {
  var expect = chai.expect;
  var assert = chai.assert;

  describe('when click on the Tieosoiteprojektit button', function() {
    this.timeout(1500000);

    before(function(done) {
      var backend = testHelpers.fakeBackend(13, testHelpers.selectTestData('roadAddress'),354810.0, 6676460.0);

      testHelpers.restartApplication(function(map) {
        eventbus.once('roadLayer:featuresLoaded', function() {
          done();
        });
      }, backend);
    });

    before(function(done) {
      $('[id^=projectListButton]:visible').prop('disabled', false);
      $('[id^=projectListButton]:visible').attr('disabled', false);
      testHelpers.clickProjectListButton();
      done();
    });

    it('open project list window', function () {
      $('[id^=projectListButton]').prop('disabled', false);
      $('[id^=projectListButton]').attr('disabled', false);
      assert($('[id^=project-window]:visible').length > 0, "Windows didn't open. Check permissions.");
    });
  });
});
