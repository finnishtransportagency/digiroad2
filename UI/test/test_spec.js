var chai = typeof exports !== "undefined" && exports !== null ? require('chai') : this.chai;
var skillet = typeof exports !== "undefined" && exports !== null ? require("../src/test") : this.skillet;
var assert = chai.assert;

describe('Array', function(){
  describe('#indexOf()', function(){
    it('should return -1 when the value is not present', function(){
      assert.equal(-1, [1,2,3].indexOf(5));
      assert.equal(-1, [1,2,3].indexOf(2));
      assert.equal('Bacon Strips', skillet.publicProperty);
    });

    it('should test another scenario', function(){
      assert.equal(1, skillet.publicMethod());
    });
  });
});
