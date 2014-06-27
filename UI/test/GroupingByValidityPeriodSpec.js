define(['chai'], function(chai) {
  var expect = chai.expect;

  describe('when loading application with overlapping bus stops in different validity periods', function() {
    it('only includes bus stops in the selected validity period to the group', function() {
      expect($('[data-asset-id=300348]')).to.be.hidden;
      expect($('[data-asset-id=300347]')).to.be.visible;
    });
  });

});