(function(root) {
  root.SpeedLimitStyle = function(applicationModel) {
    var createZoomAndTypeDependentRule = function(type, zoomLevel, style) {
      return new StyleRule().where('type').is(type).and('zoomLevel').is(zoomLevel).use(style);
    };

    var createZoomDependentOneWayRule = function(zoomLevel, style) {
      return new StyleRule().where('sideCode').isNot(1).and('zoomLevel').is(zoomLevel).use(style);
    };

    var createZoomAndTypeDependentOneWayRule = function(type, zoomLevel, style) {
      return new StyleRule().where('type').is(type).and('zoomLevel').is(zoomLevel).and('sideCode').isNot(1).use(style);
    };

    var unknownLimitStyleRule = new StyleRule().where('type').is('unknown').use({icon: {src:  'images/speed-limits/unknown.svg' }});

    var overlayStyleRule = _.partial(createZoomAndTypeDependentRule, 'overlay');
    var overlayStyleRules = [
      overlayStyleRule(9, { stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 1,  lineDash: [1,6] }}),
      overlayStyleRule(10, { stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 3,  lineDash: [1,10] }}),
      overlayStyleRule(11, { stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 5,  lineDash: [1,15] }}),
      overlayStyleRule(12, { stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 8,  lineDash: [1,22] }}),
      overlayStyleRule(13, { stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 8,  lineDash: [1,22] }}),
      overlayStyleRule(14, { stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 12, lineDash: [1,28] }}),
      overlayStyleRule(15, { stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 12, lineDash: [1,28] }})
    ];

    var oneWayOverlayStyleRule = _.partial(createZoomAndTypeDependentOneWayRule, 'overlay');
    var oneWayOverlayStyleRules = [
      oneWayOverlayStyleRule(9, { stroke: {lineDash: [1,6] }}),
      oneWayOverlayStyleRule(10, { stroke: {lineDash: [1,10] }}),
      oneWayOverlayStyleRule(11, { stroke: {lineDash: [1,10] }}),
      oneWayOverlayStyleRule(12, { stroke: {lineDash: [1,16] }}),
      oneWayOverlayStyleRule(13, { stroke: {lineDash: [1,16] }}),
      oneWayOverlayStyleRule(14, { stroke: {lineDash: [1,16] }}),
      oneWayOverlayStyleRule(15, { stroke: {lineDash: [1,16] }})
    ];

    var validityDirectionStyleRules = [
      createZoomDependentOneWayRule(9, { stroke: {width: 2 }}),
      createZoomDependentOneWayRule(10, { stroke: {width: 4 }}),
      createZoomDependentOneWayRule(11, { stroke: {width: 4 }}),
      createZoomDependentOneWayRule(12, { stroke: {width: 5 }}),
      createZoomDependentOneWayRule(13, { stroke: {width: 5 }}),
      createZoomDependentOneWayRule(14, { stroke: {width: 8 }}),
      createZoomDependentOneWayRule(15, { stroke: {width: 8 }})
    ];

    var speedLimitStyleRules = [
      new StyleRule().where('value').is(20).use({ stroke: { color: '#00ccdd', fill: '#00ccdd'}, icon: {src: 'images/speed-limits/20.svg'}}),
      new StyleRule().where('value').is(30).use({ stroke: { color: '#ff55dd', fill: '#ff55dd'}, icon: {src:  'images/speed-limits/30.svg'}}),
      new StyleRule().where('value').is(40).use({ stroke: { color: '#11bb00', fill: '#11bb00'}, icon: {src:  'images/speed-limits/40.svg'}}),
      new StyleRule().where('value').is(50).use({ stroke: { color: '#ff0000', fill: '#11bb00'}, icon: {src:  'images/speed-limits/50.svg'}}),
      new StyleRule().where('value').is(60).use({ stroke: { color: '#0011bb', fill: '#0011bb'}, icon: {src:  'images/speed-limits/60.svg'}}),
      new StyleRule().where('value').is(70).use({ stroke: { color: '#00ccdd', fill: '#00ccdd'}, icon: {src:  'images/speed-limits/70.svg'}}),
      new StyleRule().where('value').is(80).use({ stroke: { color: '#ff0000', fill: '#ff0000'}, icon: {src:  'images/speed-limits/80.svg'}}),
      new StyleRule().where('value').is(90).use({ stroke: { color: '#ff55dd', fill: '#ff55dd'}, icon: {src:  'images/speed-limits/90.svg'}}),
      new StyleRule().where('value').is(100).use({ stroke: { color: '#11bb00', fill: '#11bb00'}, icon: {src:  'images/speed-limits/100.svg'}}),
      new StyleRule().where('value').is(120).use({ stroke: { color: '#0011bb', fill: '#0011bb'}, icon: {src:  'images/speed-limits/120.svg'}})
    ];

    var speedLimitFeatureSizeRules = [
      new StyleRule().where('zoomLevel').is(9).use({ stroke: {width: 3}, pointRadius: 0 ,icon: {scale: 0.8}}),
      new StyleRule().where('zoomLevel').is(10).use({ stroke: {width: 5}, pointRadius: 10 ,icon: {scale: 1}}),
      new StyleRule().where('zoomLevel').is(11).use({ stroke: {width: 7}, pointRadius: 14 ,icon: {scale: 1.3}}),
      new StyleRule().where('zoomLevel').is(12).use({ stroke: {width: 10}, pointRadius: 16 ,icon: {scale: 1.6}}),
      new StyleRule().where('zoomLevel').is(13).use({ stroke: {width: 10}, pointRadius: 16 ,icon: {scale: 1.8}}),
      new StyleRule().where('zoomLevel').is(14).use({ stroke: {width: 14}, pointRadius: 22 ,icon: {scale: 2}}),
      new StyleRule().where('zoomLevel').is(15).use({ stroke: {width: 14}, pointRadius: 22 ,icon: {scale: 2.2}})
    ];

    var typeSpecificStyleRules = [
      new StyleRule().where('type').is('overlay').use({ stroke: {opacity: 1.0}}),
      new StyleRule().where('type').is('other').use({ stroke: {opacity: 0.7}}),
      new StyleRule().where('type').is('unknown').use({stroke: {color: '#000000', opacity: 0.6},  icon: {src: 'images/speed-limits/unknown.svg'}}),
      new StyleRule().where('type').is('cutter').use({icon: {src: 'images/cursor-crosshair.svg'}})
    ];

    var browseStyle = new StyleRuleProvider({});
    browseStyle.addRules(speedLimitStyleRules);
    browseStyle.addRules(speedLimitFeatureSizeRules);
    browseStyle.addRules(typeSpecificStyleRules);
    browseStyle.addRules(overlayStyleRules);
    browseStyle.addRules(validityDirectionStyleRules);
    browseStyle.addRules(oneWayOverlayStyleRules);

    var selectionStyle = new StyleRuleProvider({ stroke: {opacity: 0.15}, graphic: {opacity: 0.3}});
    selectionStyle.addRules(speedLimitStyleRules);
    selectionStyle.addRules(speedLimitFeatureSizeRules);
    selectionStyle.addRules(typeSpecificStyleRules);
    selectionStyle.addRules(overlayStyleRules);
    selectionStyle.addRules(validityDirectionStyleRules);
    selectionStyle.addRules(oneWayOverlayStyleRules);
    selectionStyle.addRules([unknownLimitStyleRule]);

    //History rules
    var typeSpecificStyleRulesHistory = [
        new StyleRule().where('type').is('overlay').use({ stroke: {opacity: 0.8}}),
        new StyleRule().where('type').is('other').use({ stroke: {opacity: 0.5}}),
        new StyleRule().where('type').is('unknown').use({ stroke: {color: '#000000', opacity: 0.6}, icon: {src: 'images/speed-limits/unknown.svg'}}),
        new StyleRule().where('type').is('cutter').use({icon: {src: 'images/cursor-crosshair.svg'}})
    ];

    var overlayStyleRuleHistory = _.partial(createZoomAndTypeDependentRule, 'overlay');
    var overlayStyleRulesHistory = [
        overlayStyleRuleHistory(9, { stroke: {opacity: 0.5, color: '#ffffff', lineCap: 'square', width: 1, lineDash: [1,6] }}),
        overlayStyleRuleHistory(10, { stroke: {opacity: 0.5, color: '#ffffff', lineCap: 'square', width: 1, lineDash: [1,10] }}),
        overlayStyleRuleHistory(11, { stroke: {opacity: 0.5, color: '#ffffff', lineCap: 'square', width: 2, lineDash: [1,15] }}),
        overlayStyleRuleHistory(12, { stroke: {opacity: 0.5, color: '#ffffff', lineCap: 'square', width: 4, lineDash: [1,22] }}),
        overlayStyleRuleHistory(13, { stroke: {opacity: 0.5, color: '#ffffff', lineCap: 'square', width: 4, lineDash: [1,22] }}),
        overlayStyleRuleHistory(14, { stroke: {opacity: 0.5, color: '#ffffff', lineCap: 'square', width: 5, lineDash: [1,28] }}),
        overlayStyleRuleHistory(15, { stroke: {opacity: 0.5, color: '#ffffff', lineCap: 'square', width: 5, lineDash: [1,28] }})
    ];

    var validityDirectionStyleRulesHistory = [
      createZoomDependentOneWayRule(9, { stroke: {width: 0.5 }}),
      createZoomDependentOneWayRule(10, { stroke: {width: 2 }}),
      createZoomDependentOneWayRule(11, { stroke: {width: 2 }}),
      createZoomDependentOneWayRule(12, { stroke: {width: 4 }}),
      createZoomDependentOneWayRule(13, { stroke: {width: 4 }}),
      createZoomDependentOneWayRule(14, { stroke: {width: 5 }}),
      createZoomDependentOneWayRule(15, { stroke: {width: 5 }})
    ];

    var speedLimitFeatureSizeRulesHistory = [
        new StyleRule().where('zoomLevel').is(9).use({ stroke: {width: 3}}),
        new StyleRule().where('zoomLevel').is(10).use({ stroke: {width: 5}}),
        new StyleRule().where('zoomLevel').is(11).use({ stroke: {width: 7}}),
        new StyleRule().where('zoomLevel').is(12).use({ stroke: {width: 10}}),
        new StyleRule().where('zoomLevel').is(13).use({ stroke: {width: 10}}),
        new StyleRule().where('zoomLevel').is(14).use({ stroke: {width: 14}}),
        new StyleRule().where('zoomLevel').is(15).use({ stroke: {width: 14}})
    ];

  var historyStyle = new StyleRuleProvider({ stroke: {opacity: 0.15},   graphic: {opacity: 0.3}});
    historyStyle.addRules(speedLimitStyleRules);
    historyStyle.addRules(speedLimitFeatureSizeRulesHistory);
    historyStyle.addRules(typeSpecificStyleRulesHistory);
    historyStyle.addRules(overlayStyleRulesHistory);
    historyStyle.addRules(validityDirectionStyleRulesHistory);
    historyStyle.addRules(oneWayOverlayStyleRules);

    var isUnknown = function(speedLimit) {
      return !_.isNumber(speedLimit.value);
    };

    var lineFeatures = function(speedLimits) {
      return _.map(speedLimits, function(speedLimit) {
        var points = _.map(speedLimit.points, function(point) {
          return [point.x, point.y];
        });
        var type = isUnknown(speedLimit) ? { type: 'unknown' } : {};
        var attributes = _.merge(_.cloneDeep(speedLimit), type);
        var feature = new ol.Feature(new ol.geom.LineString(points));
        feature.setProperties(_.omit(attributes, 'geometry'));
        return feature;
      });
    };

    var dottedLineFeatures = function(speedLimits) {
      var solidLines = lineFeatures(speedLimits);
      var dottedOverlay = lineFeatures(_.map(speedLimits, function(limit) { return _.merge({}, limit, { type: 'overlay' }); }));
      return solidLines.concat(dottedOverlay);
    };

    var limitSigns = function(speedLimits) {
      return _.map(speedLimits, function(speedLimit) {
        var points = _.map(speedLimit.points, function(point) {
          return [point.x, point.y];
        });
        var road = new ol.geom.LineString(points);
        var signPosition = GeometryUtils.calculateMidpointOfLineString(road);
        var type = isUnknown(speedLimit) ? { type: 'unknown' } : {};
        var attributes = _.merge(_.cloneDeep(speedLimit), type);

        var feature = new ol.Feature(new ol.geom.Point([signPosition.x, signPosition.y]));
        feature.setProperties(_.omit(attributes, 'geometry'));
        return feature;
      });
    };

    var renderFeatures = function(speedLimits) {
       var speedLimitsWithType = _.map(speedLimits, function(limit) { return _.merge({}, limit, { type: 'other' }); });
      var offsetBySideCode = function(speedLimit) {
        return GeometryUtils.offsetBySideCode(applicationModel.zoom.level, speedLimit);
      };
      var speedLimitsWithAdjustments = _.map(speedLimitsWithType, offsetBySideCode);
      var speedLimitsSplitAt70kmh = _.groupBy(speedLimitsWithAdjustments, function(speedLimit) { return speedLimit.value >= 70; });
      var lowSpeedLimits = speedLimitsSplitAt70kmh[false];
      var highSpeedLimits = speedLimitsSplitAt70kmh[true];

      return lineFeatures(lowSpeedLimits)
        .concat(dottedLineFeatures(highSpeedLimits))
        .concat(limitSigns(speedLimitsWithAdjustments));
     };

    return {
      browsingStyle: browseStyle,
      selectionStyle: selectionStyle,
      historyStyle: historyStyle,
      renderFeatures: renderFeatures
    };
  };
})(this);
