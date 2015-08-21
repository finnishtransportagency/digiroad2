define(['chai', 'LocationInputParser'], function(chai, LocationInputParser) {
  var assert = chai.assert;

  it('parses coordinate pairs', function() {
    assert.deepEqual({ type: 'coordinate', lat: 123, lon: 345 }, LocationInputParser.parse('123,345'));
  });

  it('parses street addresses', function() {
    assert.deepEqual({ type: 'street', street: 'Salorankatu 7', municipality: 'Salo' }, LocationInputParser.parse('Salorankatu 7, Salo'));
  });

  it('parses road addresses', function() {
    assert.deepEqual({ type: 'road', roadNumber: 52, section: 1, distance: 100 }, LocationInputParser.parse('52, 1, 100'));
  });

  it('returns validation error on unexpected input', function() {
    assert.deepEqual({ type: 'invalid' }, LocationInputParser.parse('234, 345 NOT VALID'));
  });
});
