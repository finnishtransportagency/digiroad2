define(['chai', 'LocationInputParser'], function(chai, LocationInputParser) {
  var expect = chai.expect;

  it('parses coordinate pairs', function() {
    expect(LocationInputParser.parse('123,345')).to.deep.equal({ type: 'coordinate', lat: 123, lon: 345 });
  });

  it('parses street addresses', function() {
    expect(LocationInputParser.parse('Salorankatu, Salo')).to.deep.equal({ type: 'street', address: 'Salorankatu, Salo' });
    expect(LocationInputParser.parse('Salorankatu 7, Salo')).to.deep.equal({ type: 'street', address: 'Salorankatu 7, Salo' });
    expect(LocationInputParser.parse('Salorankatu 7, Salo')).to.deep.equal({ type: 'street', address: 'Salorankatu 7, Salo' });
    expect(LocationInputParser.parse('Peräkylä, Imatra')).to.deep.equal({ type: 'street', address: 'Peräkylä, Imatra' });
    expect(LocationInputParser.parse('Iso Roobertinkatu, Helsinki')).to.deep.equal({ type: 'street', address: 'Iso Roobertinkatu, Helsinki' });
    expect(LocationInputParser.parse('Kirkkokatu, Peräseinäjoki')).to.deep.equal({ type: 'street', address: 'Kirkkokatu, Peräseinäjoki' });
    expect(LocationInputParser.parse('Kirkkokatu')).to.deep.equal({ type: 'street', address: 'Kirkkokatu' });
    expect(LocationInputParser.parse('Kirkkokatu 2')).to.deep.equal({ type: 'street', address: 'Kirkkokatu 2' });
  });

  it('parses road addresses', function() {
    expect(LocationInputParser.parse('52 1 100 0')).to.deep.equal({ type: 'road', roadNumber: 52, section: 1, distance: 100, lane: 0 });
    expect(LocationInputParser.parse('52 1 100')).to.deep.equal({ type: 'road', roadNumber: 52, section: 1, distance: 100 });
    expect(LocationInputParser.parse('52\t1 100')).to.deep.equal({ type: 'road', roadNumber: 52, section: 1, distance: 100 });
    expect(LocationInputParser.parse('52 1')).to.deep.equal({ type: 'road', roadNumber: 52, section: 1 });
    expect(LocationInputParser.parse('52')).to.deep.equal({ type: 'idOrRoadNumber', text: '52' });
    expect(LocationInputParser.parse('139053')).to.deep.equal({ type: 'idOrRoadNumber', text: '139053' });
    expect(LocationInputParser.parse('13905300000000000')).to.deep.equal({ type: 'idOrRoadNumber', text: '13905300000000000' });
    expect(LocationInputParser.parse('O139053','massTransitStop')).to.deep.equal({ type: 'liviId', text: 'O139053' });
    expect(LocationInputParser.parse('OTHJ1','massTransitStop')).to.deep.equal({ type: 'liviId', text: 'OTHJ1' });
    expect(LocationInputParser.parse('E1','massTransitStop')).to.deep.equal({ type: 'liviId', text: 'E1' });
    expect(LocationInputParser.parse('E11','massTransitStop')).to.deep.equal({ type: 'liviId', text: 'E11' });
    expect(LocationInputParser.parse('EE111','massTransitStop')).to.deep.equal({ type: 'liviId', text: 'EE111' });
    expect(LocationInputParser.parse('52   1')).to.deep.equal({ type: 'road', roadNumber: 52, section: 1 });

  });

  it('returns validation error on unexpected input', function() {
    expect(LocationInputParser.parse('234, 345 NOT VALID')).to.deep.equal({ type: 'invalid' });
    expect(LocationInputParser.parse('123.234')).to.deep.equal({ type: 'invalid' });
    expect(LocationInputParser.parse('123.234,345.123')).to.deep.equal({ type: 'invalid' });
    expect(LocationInputParser.parse('')).to.deep.equal({ type: 'invalid' });
  });
});
