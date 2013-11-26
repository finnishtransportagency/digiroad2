var assert = chai.assert;

describe('Geometry calculations: distance from line', function(){
    var fut = geometrycalculator.getDistanceFromLine;
    it('point is in line', function(){
        assert.equal(0, fut({ start: { x: 0.0, y: 0.0 }, end: { x: 1.0, y: 1.0 }}, { x: 0.5, y: 0.5 }));
        assert.equal(0, fut({ start: { x: 1.0, y: 1.0 }, end: { x: 2.0, y: 2.0 }}, { x: 1.5, y: 1.5 }));
        assert.equal(0, fut({ start: { x: 0.0, y: 0.0 }, end: { x: 2.0, y: 1.0 }}, { x: 1.0, y: 0.5 }));
    });

    it('point is not in line', function(){
        assert.equal(Math.sqrt(2) / 2, fut({ start: { x: 0.0, y: 0.0 }, end: { x: 1.0, y: 1.0 }}, { x: 1.0, y: 0.0 }));
        assert.equal(1, fut({ start: { x: 0.0, y: 0.0 }, end: { x: 1.0, y: 1.0 }}, { x: 1.0, y: 2.0 }));
        assert.equal(0.2, fut({ start: { x: 0.0, y: 0.0 }, end: { x: 4.0, y: 3.0 }}, { x: 1.0, y: 1.0 }));
    });

    it('point is not in line, scenarios 2', function(){
        assert.equal(0.40, fut({ start: { x: 0.0, y: 4.0 }, end: { x: 3.0, y: 0.0 }}, { x: 1.0, y: 2.0 }).toFixed(2));
        assert.equal(0.20, fut({ start: { x: 0.0, y: 4.0 }, end: { x: 3.0, y: 0.0 }}, { x: 2.0, y: 1.0 }).toFixed(2));
        assert.equal(5.2, fut({ start: { x: 0.0, y: 4.0 }, end: { x: 3.0, y: 0.0 }}, { x: 5.0, y: 6.0 }).toFixed(2));
    });
});

describe('Geometry calculations: nearest point in line', function(){
    var fut = geometrycalculator.nearestPointOnLine;
    it('should return zero as distance', function(){
        assert.deepEqual({ x: 0.5, y: 0.5 }, fut({ start: { x: 0.0, y: 0.0 }, end: { x: 1.0, y: 1.0 }}, { x: 0.5, y: 0.5 }));
        assert.deepEqual({ x: 1.5, y: 1.5 }, fut({ start: { x: 1.0, y: 1.0 }, end: { x: 2.0, y: 2.0 }}, { x: 1.5, y: 1.5 }));
        assert.deepEqual({ x: 1.0, y: 0.5 }, fut({ start: { x: 0.0, y: 0.0 }, end: { x: 2.0, y: 1.0 }}, { x: 1.0, y: 0.5 }));
    });
    it('should return rigth value as distance', function(){
        assert.deepEqual({ x: 0.5, y: 0.5 }, fut({ start: { x: 0.0, y: 0.0 }, end: { x: 1.0, y: 1.0 }}, { x: 1.0, y: 0.0 }));
        assert.deepEqual({ x: 1.0, y: 1.0 }, fut({ start: { x: 0.0, y: 0.0 }, end: { x: 1.0, y: 1.0 }}, { x: 1.0, y: 2.0 }));
        assert.deepEqual({ x: 1.12, y: 0.8400000000000001 }, fut({ start: { x: 0.0, y: 0.0 }, end: { x: 4.0, y: 3.0 }}, { x: 1.0, y: 1.0 }));
    });

    it('should return rigth value as distance scenario2', function(){
        assert.deepEqual({ x: 1.32, y: 2.24 }, fut({ start: { x: 0.0, y: 4.0 }, end: { x: 3.0, y: 0.0 }}, { x: 1.0, y: 2.0 }));
        assert.deepEqual({ x: 2.16, y: 1.12 }, fut({ start: { x: 0.0, y: 4.0 }, end: { x: 3.0, y: 0.0 }}, { x: 2.0, y: 1.0 }));
        assert.deepEqual({ x: 0.8400000000000001, y: 2.88 }, fut({ start: { x: 0.0, y: 4.0 }, end: { x: 3.0, y: 0.0 }}, { x: 5.0, y: 6.0 }));
    });
});