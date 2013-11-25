var assert = chai.assert;

describe('Geometry calculations', function(){
    before(function(){
    });

    describe('points in line', function(){
        it('should return zero as distance', function(){
            assert.equal(0, geometrycalculator.getDistanceFromLine(0.0, 0.0, 1.0, 1.0, 0.5, 0.5));
            assert.equal(0, geometrycalculator.getDistanceFromLine(1.0, 1.0, 2.0, 2.0, 1.5, 1.5));
            assert.equal(0, geometrycalculator.getDistanceFromLine(0.0, 0.0, 2.0, 1.0, 1.0, 0.5));
        });
    });

    describe('points not in line', function(){
        it('should return rigth value as distance', function(){
            assert.equal(Math.sqrt(2) / 2, geometrycalculator.getDistanceFromLine(0.0, 0.0, 1.0, 1.0, 1.0, 0.0));
            assert.equal(1, geometrycalculator.getDistanceFromLine(0.0, 0.0, 1.0, 1.0, 1.0, 2.0));
            assert.equal(0.2, geometrycalculator.getDistanceFromLine(0.0, 0.0, 4.0, 3.0, 1.0, 1.0));
        });

        it('should return rigth value as distance scenario2', function(){
            assert.equal(0.40, geometrycalculator.getDistanceFromLine(0.0, 4.0, 3.0, 0.0, 1.0, 2.0).toFixed(2));
            assert.equal(0.20, geometrycalculator.getDistanceFromLine(0.0, 4.0, 3.0, 0.0, 2.0, 1.0).toFixed(2));
            assert.equal(5.2,  geometrycalculator.getDistanceFromLine(0.0, 4.0, 3.0, 0.0, 5.0, 6.0).toFixed(2));
        });
    });
});