define(['chai', 'LocationInputParser'], function(chai, LocationInputParser) {
    var assert = chai.assert;

    it('parses coordinate pairs', function() {
        assert.deepEqual({ type: 'coordinate', lat: 123, lon: 345 }, LocationInputParser.parse('123,345'));
    });

    it('parses street addresses', function() {
        assert.deepEqual({ type: 'street', street: 'Salorankatu 7', municipality: 'Salo' }, LocationInputParser.parse('Salorankatu 7, Salo'));
    });
});
