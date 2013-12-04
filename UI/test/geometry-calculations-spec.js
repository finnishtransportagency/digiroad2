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

describe('Geometry calculations: nearest line', function(){
    var fut = geometrycalculator.findNearestLine;
    var set1 = {
        id: 1,
        attributes: {
            roadLinkId: "roadLink1"
        },
        geometry: {
            components: [{ x: 0.0, y: 0.0 }, { x: 1.0, y: 1.0 },
                         { x: 2.0, y: 1.0 }, { x: 2.0, y: 2.0 } ] } };
    var set2 = {
        id: 2,
        attributes: {
            roadLinkId: "roadLink2"
        },
        geometry: {
            components: [{ x: 0.0, y: 1.0 }, { x: 1.0, y: 2.0 },
                         { x: 2.0, y: 2.0 }, { x: 2.0, y: 3.0 } ] } };

    it('perf', function(){
        var tmp = [];
        for(var i = 0; i < 1000; i++){
            tmp.push(set1);
        }
        console.time('perf');
        fut(tmp, 0.5, 0.5);
        console.timeEnd('perf');
    });

    it('should return correct line if in first set, first line', function(){
        assert.deepEqual({ id: 1, roadLinkId: "roadLink1", start: { x: 0, y: 0 }, end: { x: 1, y: 1 } },
                           fut([set1, set2], 0.5, 0.5));
    });

    it('should return correct line if in first set, not first line ', function(){
        assert.deepEqual({ id: 1, roadLinkId: "roadLink1", start: { x: 2, y: 1 }, end: { x: 2, y: 2 } },
                           fut([set1, set2], 1.8, 1.5));
    });

    it('should return correct line if not in first set, first line', function(){
        assert.deepEqual({ id: 2, roadLinkId: "roadLink2", start: { x: 0, y: 1 }, end: { x: 1, y: 2 } },
            fut([set1, set2], 0, 2.0));
    });

    it('should return correct line if not in first set, not first line ', function(){
        assert.deepEqual({ id: 2, roadLinkId: "roadLink2", start: { x: 2, y: 2 }, end: { x: 2, y: 3 } },
            fut([set1, set2], 1.8, 2.5));
    });
});

describe('Geometry detection is point in the circle', function(){
    var fut = geometrycalculator.isInCircle;

    it('should return true', function(){
        assert.equal(true, fut(0,0,2,1,1));
    });

    it('should return false', function(){
        assert.equal(false, fut(0,0,1,1,1));
    });
});

describe('Geometry calculations: radian to degree', function(){
    var fut = geometrycalculator.rad2deg;

    it ('should return 180', function() {
        assert.equal(180, fut(Math.PI));
    });

});

describe('Geometry calculations: degree to radian', function(){
    var fut = geometrycalculator.deg2rad;

    it ('should return PI value', function() {
        assert.equal(Math.PI, fut(180));
    });
});

describe('Geometry calculations: line direction angle', function(){
    var fut = geometrycalculator.getLineDirectionRadAngle;
    var fut2 = geometrycalculator.getLineDirectionDegAngle;

    var origin = { x: 0.0, y: 0.0 };
    var line = { start: origin,  end: { x: 0.0, y: 1.0 } };
    var line2 = { start: origin,  end: { x: 1.0, y: 1.0 } };
    var line3 = { start: origin,  end: { x: 1.0, y: 0.0 } };
    var line4 = { start: origin,  end: { x: 1.0, y: -1.0 } };
    var line5 = { start: origin,  end: { x: 0.0, y: -1.0 } };
    var line6 = { start: origin,  end: { x: -1.0, y: -1.0 } };
    var line7 = { start: origin,  end: { x: -1.0, y: 0.0 } };
    var line8 = { start: origin,  end: { x: -1.0, y: 1.0 } };

    it ('should return 3.14159265358979 radian value', function() {
        assert.equal(3.141592653589793, fut(line));
    });

    it ('should return 360 degree value', function() {
        assert.equal(360, fut2(line));
    });

    it ('should return -2.356194490192345 radian value', function() {
        assert.equal(-2.356194490192345, fut(line2));
    });

    it ('should return 45 degree value', function() {
        assert.equal(45, fut2(line2));
    });

    it ('should return -1.5707963267948966 radian value', function() {
        assert.equal(-1.5707963267948966, fut(line3));
    });

    it ('should return 90 degree value', function() {
        assert.equal(90, fut2(line3));
    });

    it ('should return -0.7853981633974483 radian value', function() {
        assert.equal(-0.7853981633974483, fut(line4));
    });

    it ('should return 135 degree value', function() {
        assert.equal(135, fut2(line4));
    });

    it ('should return 0 radian value', function() {
        assert.equal(0, fut(line5));
    });

    it ('should return 180 degree value', function() {
        assert.equal(180, fut2(line5));
    });

    it ('should return 0.7853981633974483 radian value', function() {
        assert.equal(0.7853981633974483, fut(line6));
    });

    it ('should return 225 degree value', function() {
        assert.equal(225, fut2(line6));
    });

    it ('should return 1.5707963267948966 radian value', function() {
        assert.equal(1.5707963267948966, fut(line7));
    });

    it ('should return 270 degree value', function() {
        assert.equal(270, fut2(line7));
    });

    it ('should return 2.356194490192345 radian value', function() {
        assert.equal(2.356194490192345, fut(line8));
    });

    it ('should return 315 degree value', function() {
        assert.equal(315, fut2(line8));
    });
});
