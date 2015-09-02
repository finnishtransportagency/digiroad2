define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  // 1. Select link property layer
  // 2. Assert: box control deactivated
  // 3. Select edit mode
  // 4. Assert: box control activated
  // 5. Select speed limit layer
  // 6. Assert: box control deactivated
  describe('when loading application in link property layer', function() {
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        testHelpers.selectLayer('linkProperty');
        done();
      });
    });
    it('Implement me', function() {
      console.log('**** Implement me');
    });
  });
});
