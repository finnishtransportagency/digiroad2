(function (root) {
  root.ProjectLinkStyler = function () {

    var notHandledStatus = 0;
    var unchangedStatus = 1;
    var newRoadAddressStatus = 2;
    var transferredStatus = 3;
    var numberingStatus = 4;
    var terminatedStatus = 5;
    var unknownStatus = 99;

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
      strokeWidthRule(12, { stroke: {width: 7}}),
      strokeWidthRule(13, { stroke: {width: 10}}),
      strokeWidthRule(14, { stroke: {width: 14}}),
      strokeWidthRule(15, { stroke: {width: 14}})
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
      new StyleRule().where('status').is(notHandledStatus).use({stroke: {color: '#00FF00'}}),
      new StyleRule().where('status').is(unchangedStatus).use({stroke: {color: '#00FF00'}}),
      new StyleRule().where('status').is(newRoadAddressStatus).use({stroke: {color: '#00FF00'}}),
      new StyleRule().where('status').is(transferredStatus).use({stroke: {color: '#00FF00'}}),
      new StyleRule().where('status').is(numberingStatus).use({stroke: {color: '#00FF00'}}),
      new StyleRule().where('status').is(terminatedStatus).use({stroke: {color: '#00FF00'}}),
      new StyleRule().where('roadLinkSource').is(3).use({stroke: {color: '#00FF00'}}),
      new StyleRule().where('anomaly').is(3).and(status).is(unknownStatus).use({stroke: {color: '#00FF00'}}),
      new StyleRule().where('roadClass').is(99).use({stroke: {color: '#00FF00'}})
    ];

    var projectLinkStyle = new StyleRuleProvider({});
    projectLinkStyle.addRules(projectLinkRules);
    projectLinkStyle.addRules(strokeWidthRules);

    var selectionLinkStyle = new StyleRuleProvider({opacity: 0.95, lineCap: 'round'});
    selectionLinkStyle.addRules(projectLinkRules);
    selectionLinkStyle.addRules(strokeWidthRules);
    selectionLinkStyle.addRules(selectionStyleRules);

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
