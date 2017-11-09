(function (root) {
  root.RoadLinkStyler = function ( projectmode) {
    /**
     * RoadLinkstyler is styler for normal roadlinks in projectmode for setting them opacity. Does not include linedashes since we are not sure if those will be included in project mode
     */

    var projectMode=projectmode;


    var strokeByZomLevel = function (zoomLevel, style) {
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



    var projectStyles= [
      new StyleRule().where('roadClass').is(1).use({stroke: {color: 'rgba(255, 0, 0, 0.05)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(2).use({stroke: {color: 'rgba(255, 102, 0, 0.05)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(3).use({stroke: {color: 'rgba(255, 153, 51,0.05)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(4).use({stroke: {color: 'rgba(0, 17, 187, 0.05)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(5).use({stroke: {color: 'rgba(51, 204, 204, 0.05)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(6).use({stroke: {color: 'rgba(224, 29, 217,0.05)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(7).use({stroke: {color: 'rgba(0, 204, 221,0.05)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(8).use({stroke: {color: 'rgba(252, 109, 160, 0.05)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(9).use({stroke: {color: 'rgba(255, 85, 221, 0.05)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(10).use({stroke: {color: 'rgba(255, 85, 221 0.05)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(11).use({stroke: {color: 'rgba(68, 68, 68, 0.1)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(97).use({stroke: {color: 'rgba(rgba(30, 30, 30, 0.1)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(98).use({stroke: {color: 'rgba(250, 250, 250, 0.1)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('constructionType').is(1).use({stroke: {color: 'rgba(255, 153, 0, 0.1)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('gapTransfering').is(true).use({stroke: {color: 'rgb(0, 255, 0, 0.1)', width: 8, lineCap: 'round'}}),
      new StyleRule().where('roadClass').is(99).use({stroke: {color: 'rgba(164, 164, 162, 0.05)', width: 8, lineCap: 'round'}})

    ];

    var styleChooser = function () {
      if (projectMode) return projectStyles;
      else
        return normalStyles;
    };

    var selectionStyleRules = [
      new StyleRule().where('roadClass').isDefined().use({stroke: {color: '#00FF00'}})
    ];


    var projectLinkStyle = new StyleRuleProvider({});
    projectLinkStyle.addRules(projectStyles);
    projectLinkStyle.addRules(strokeWidthRules);

    var selectionLinkStyle = new StyleRuleProvider({opacity: 0.95, lineCap: 'round', width: 8});
    selectionLinkStyle.addRules(projectStyles);
    selectionLinkStyle.addRules(strokeWidthRules);
    selectionLinkStyle.addRules(selectionStyleRules);

    var getRoadLinkStyle = function () {
      return projectLinkStyle;
    };

    var getSelectionLinkStyle = function () {
      return selectionLinkStyle;
    };

    return {
      getRoadLinkStyle: getRoadLinkStyle,
      getSelectionLinkStyle: getSelectionLinkStyle
    };
  };
})(this);
