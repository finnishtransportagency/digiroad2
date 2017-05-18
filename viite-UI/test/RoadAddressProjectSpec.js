/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  var assert = chai.assert;

  describe('when click on the Tieosoiteprojektit button', function() {
    this.timeout(1500000);

    before(function(done) {
      testHelpers.clickProjectListButton();
      done();
    });

    it('open project list window', function () {
       assert($('#project-window:visible').length > 0, "Windows didn't open. Check permissions.");
    });


  });

  describe('when click on the Tieosoiteprojektit button when editing a floating road', function() {
    this.timeout(1500000);

    before(function(done) {
      eventbus.trigger('layer:enableButtons', true);
      testHelpers.clickProjectListButton();
      done();
    });

    it('do not open project list window', function () {
      assert($('#project-window:visible').length === 0, "Windows shouldn't open.");
      eventbus.trigger('layer:enableButtons', false);
    });

  });

});
