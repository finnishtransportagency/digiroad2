define(['chai'], function(chai) {
  var assert = chai.assert;

  describe('when loading application with overlapping bus stops in different validity periods', function() {
    it('only includes bus stops in the selected validity period to the group', function() {
      assert.isTrue($('[data-asset-id=300348]').is(':hidden'));
      assert.isTrue($('[data-asset-id=300347]').is(':visible'));
    });
  });

});