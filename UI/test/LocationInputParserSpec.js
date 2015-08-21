define(['chai', 'LocationInputParser'], function(chai, LocationInputParser) {
  var expect = chai.expect;

  it('parses coordinate pairs', function() {
   expect(LocationInputParser.parse('123,345')).to.deep.equal({ type: 'coordinate', lat: 123, lon: 345 });
  });

  it('parses street addresses', function() {
    expect(LocationInputParser.parse('Salorankatu 7, Salo')).to.deep.equal({ type: 'street', street: 'Salorankatu 7', municipality: 'Salo' });
    expect(LocationInputParser.parse('   Salorankatu   7,   Salo  ')).to.deep.equal({ type: 'street', street: 'Salorankatu 7', municipality: 'Salo' });
  });

  it('parses road addresses', function() {
   expect(LocationInputParser.parse('52, 1, 100')).to.deep.equal({ type: 'road', roadNumber: 52, section: 1, distance: 100 });
  });

  it('returns validation error on unexpected input', function() {
    expect(LocationInputParser.parse('234, 345 NOT VALID')).to.deep.equal({ type: 'invalid' });
  });
});
