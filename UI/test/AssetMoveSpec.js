/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  var assert = chai.assert;

  describe('when loading application with two bus stops', function() {
    before(function(done) { testHelpers.restartApplication(done); });
    describe('and moving bus stop', function() {
      describe('and canceling bus stop move', function() {
        it('returns bus stop to original location', function() {
        });
      });
    });
  });
});