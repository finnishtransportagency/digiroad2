(function (root) {
  root.RoadLinkStyler = function ( projectmode) {

    var projectMode=projectmode;
    var opacityMultiplier= 1;
    var roadNormalType = 0;
    var borderWidth = 3;
    var dashedLinesRoadClasses = [7, 8, 9, 10];
    var LINKSOURCE_NORMAL = 1;
    var LINKSOURCE_COMPLEM = 2;
    var LINKSOURCE_SURAVAGE = 3;
    var LINKSOURCE_FROZEN = 4;
    var LINKSOURCE_HISTORIC = 5;
    var LINKTYPE_NORMAL = 0;
    var LINKTYPE_COMPLEM = 1;
    var LINKTYPE_UNKNOWN = 3;
    var LINKTYPE_FLOATING = -1;
    var PROJECTLINKSTATUS_NOTHANDLED = 0;
    var PROJECTLINKSTATUS_TERMINATED = 1;


    var strokeByZomLevel = function (zoomLevel, style, roadLinkType, anomaly, roadLinkSource, notSelection, constructionType) {
      return new StyleRule().where('zoomLevel').is(zoomLevel).use(style);
    };

    var strokeWidthRule = _.partial(strokeByZomLevel);
    var strokeWidthRules = [
      strokeWidthRule(5, { stroke: {width: 1}}),
      strokeWidthRule(6, { stroke: {width: 1}}),
      strokeWidthRule(7, { stroke: {width: 2}}),
      strokeWidthRule(8, { stroke: {width: 2}}),
      strokeWidthRule(9, { stroke: {width: 2}}),
      strokeWidthRule(10, { stroke: {width: 3}}),
      strokeWidthRule(11, { stroke: {width: 3}}),
      strokeWidthRule(12, { stroke: {width: 5}}),
      strokeWidthRule(13, { stroke: {width: 8}}),
      strokeWidthRule(14, { stroke: {width: 12}}),
      strokeWidthRule(15, { stroke: {width: 12}})
    ];




    var normalStyles= [
      new StyleRule().where('roadClass').is(1).use({stroke: {color: 'rgba(255, 0, 0, 0.65)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(2).use({stroke: {color: 'rgba(255, 0, 0, 0.65)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(3).use({stroke: {color: 'rgba(255, 0, 0, 0.65)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(4).use({stroke: {color: 'rgba(255, 0, 0, 0.65)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(5).use({stroke: {color: 'rgba(255, 0, 0, 0.65)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(6).use({stroke: {color: 'rgba(255, 0, 0, 0.65)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(7).use({stroke: {color: 'rgba(255, 0, 0, 0.65)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(8).use({stroke: {color: 'rgba(255, 0, 0, 0.65)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(9).use({stroke: {color: 'rgba(255, 0, 0, 0.65)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(10).use({stroke: {color: 'rgba(255, 0, 0, 0.65)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(11).use({stroke: {color: 'rgba(255, 0, 0, 0.65)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(12).use({stroke: {color: 'rgba(255, 0, 0, 0.65)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(97).use({stroke: {color: 'rgba(255, 0, 0, 0.65)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(98).use({stroke: {color: 'rgba(255, 0, 0, 0.65)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(99).use({stroke: {color: 'rgba(255, 0, 0, 0.65)'}})];

    var projectStyles= [
      new StyleRule().where('status').is(notHandledStatus).use({stroke: {color: '#F7FE2E', width: 8, lineCap: 'round'}}),
      new StyleRule().where('status').is(unchangedStatus).use({stroke: {color: '#008080', width: 5, lineCap: 'round'}}),
      new StyleRule().where('status').is(newRoadAddressStatus).use({stroke: {color: '#FF55DD', width: 5, lineCap: 'round'}}),
      new StyleRule().where('status').is(transferredStatus).use({stroke: {color: '#ffad99', width: 3, lineCap: 'round'}}),
      new StyleRule().where('status').is(numberingStatus).use({stroke: {color: '#8B4513', width: 5, lineCap: 'round'}}),
      new StyleRule().where('status').is(terminatedStatus).use({stroke: {color: '#383836', width: 3, lineCap: 'round'}}),
      new StyleRule().where('status').is(unknownStatus).use({stroke: {color: '#383836', width: 3, lineCap: 'round'}}),
      new StyleRule().where('roadLinkSource').is(3).and('status').is(unknownStatus).use({stroke: {color: '#D3AFF6'}})
    ];

    var styleChooser = function () {
      if (projectMode) return projectStyles;
      else
        return normalStyles;
    };
    var roadLinkRules= modeChooser();




    var selectionStyleRules = [
      new StyleRule().where('roadClass').(notHandledStatus).use({stroke: {color: '#00FF00'}})
    ];

    var cutterStyleRules = [
      new StyleRule().where('type').is('cutter-crosshair').use({icon: {src: 'images/cursor-crosshair.svg'}})
    ];

    var projectLinkStyle = new StyleRuleProvider({});
    projectLinkStyle.addRules(projectLinkRules);
    projectLinkStyle.addRules(strokeWidthRules);

    var selectionLinkStyle = new StyleRuleProvider({opacity: 0.95, lineCap: 'round', width: 8});
    selectionLinkStyle.addRules(projectLinkRules);
    selectionLinkStyle.addRules(strokeWidthRules);
    selectionLinkStyle.addRules(selectionStyleRules);
    selectionLinkStyle.addRules(cutterStyleRules);

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
