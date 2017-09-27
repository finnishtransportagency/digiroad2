(function (root) {
  root.ProjectLinkStyler = function () {

    //var noAddressAnomaly = 1;
    var notHandledStatus = 0;
    var unchangedStatus = 1;
    var newRoadAddressStatus = 2;
    var transferredStatus = 3;
    var numberingStatus = 4;
    var terminatedStatus = 5;
    //var unknownStatus = 99;

    var strokeByZomLevel = function (zoomLevel, style) {
      return new StyleRule().where('zoomLevel').is(zoomLevel).use(style);
    };

    var strokeWidthRule = _.partial(strokeByZomLevel);
    var strokeWidthRules = [
      strokeWidthRule(5, { stroke: {width: 3}}),
      strokeWidthRule(6, { stroke: {width: 3}}),
      strokeWidthRule(7, { stroke: {width: 4}}),
      strokeWidthRule(8, { stroke: {width: 4}}),
      strokeWidthRule(9, { stroke: {width: 4}}),
      strokeWidthRule(10, { stroke: {width: 5}}),
      strokeWidthRule(11, { stroke: {width: 5}}),
      strokeWidthRule(12, { stroke: {width: 6}}),
      strokeWidthRule(13, { stroke: {width: 9}}),
      strokeWidthRule(14, { stroke: {width: 12}}),
      strokeWidthRule(15, { stroke: {width: 12}})
    ];

    var roadLinkRules = [
      new StyleRule().where('roadLinkSource').is(3).use({stroke: {color: '#D3AFF6', opacity: 0.65}}),
      new StyleRule().where('anomaly').isNot(1).and('roadLinkType').is(-1).and('constructionType').is('1').use({stroke: {color: '#A4A4A2', opacity: 0.65}}),
      new StyleRule().where('anomaly').isNot(1).and('roadLinkType').is(-1).and('constructionType').isNot('1').use({stroke: {color: '#F7FE2E', opacity: 0.45}}),
      new StyleRule().where('roadClass').is(1).use({stroke: {color: '#FF0000', opacity: 0.65}}),
      new StyleRule().where('roadClass').is(2).use({stroke: {color: '#FF6600', opacity: 0.65}}),
      new StyleRule().where('roadClass').is(3).use({stroke: {color: '#FF9933', opacity: 0.65}}),
      new StyleRule().where('roadClass').is(4).use({stroke: {color: '#0011BB', opacity: 0.65}}),
      new StyleRule().where('roadClass').is(5).use({stroke: {color: '#33CCCC', opacity: 0.65}}),
      new StyleRule().where('roadClass').is(6).use({stroke: {color: '#E01DD9', opacity: 0.65}}),
      new StyleRule().where('roadClass').is(7).use({stroke: {color: '#00CCDD', opacity: 0.65}}),
      new StyleRule().where('roadClass').is(8).use({stroke: {color: '#FC6DA0', opacity: 0.65}}),
      new StyleRule().where('roadClass').is(9).use({stroke: {color: '#FF55DD', opacity: 0.65}}),
      new StyleRule().where('roadClass').is(10).use({stroke: {color: '#FF55DD', opacity: 0.65}}),
      new StyleRule().where('roadClass').is(11).use({stroke: {color: '#444444', opacity: 0.75}}),
      new StyleRule().where('roadClass').is(97).use({stroke: {color: '#1e1e1e'}}),
      new StyleRule().where('roadClass').is(98).use({stroke: {color: '#fafafa'}}),
      new StyleRule().where('roadClass').is(99).use({stroke: {color: '#a4a4a2', opacity: 0.65}}),
      new StyleRule().where('anomaly').is(1).use({stroke: {color: '#383836'}}),
      new StyleRule().where('anomaly').is(1).and('constructionType').is(1).use({stroke: {color: '#ff9900', opacity: 0.95}}),
      new StyleRule().where('anomaly').is(1).and('gapTransfering').is(true).use({stroke: {color: '#00ff00', opacity: 0.75}})
    ];

    var projectLinkRules = [
      new StyleRule().where('status').is(notHandledStatus).use({stroke: {color: '#F7FE2E', width: 8, lineCap: 'round'}}),
      new StyleRule().where('status').is(unchangedStatus).use({stroke: {color: '#0000FF', width: 5, lineCap: 'round'}}),
      new StyleRule().where('status').is(newRoadAddressStatus).use({stroke: {color: '#FF55DD', width: 5, lineCap: 'round'}}),
      new StyleRule().where('status').is(transferredStatus).use({stroke: {color: '#FF0000', width: 3, lineCap: 'round'}}),
      new StyleRule().where('status').is(numberingStatus).use({stroke: {color: '#8B4513', width: 5, lineCap: 'round'}}),
      new StyleRule().where('status').is(terminatedStatus).use({stroke: {color: '#383836', width: 3, lineCap: 'round'}})
    ];

    var selectionStyleRules = [
      new StyleRule().use({fill: {color: '#00FF00', opacity: 0.75}, stroke: {color: '#00FF00', opacity: 0.95, width: 8}})
    ];

    var projectLinkStyle = new StyleRuleProvider({});
    projectLinkStyle.addRules(roadLinkRules);
    projectLinkStyle.addRules(projectLinkRules);
    projectLinkStyle.addRules(strokeWidthRules);

    var selectionLinkStyle = new StyleRuleProvider({});
    selectionLinkStyle.addRules(selectionStyleRules);
    //selectionLinkStyle.addRules(strokeWidthRules);

    var getProjectLinkStyle = function () {
      return projectLinkStyle;
    };

    var getSelectionLinkStyle = function () {
      return selectionLinkStyle;
    };

    return {
      getProjectLinkStyle: getProjectLinkStyle,
      getSelectionLinkStyle: getSelectionLinkStyle
    };
  };
})(this);
