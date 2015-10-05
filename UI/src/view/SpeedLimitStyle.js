(function(root) {
  root.SpeedLimitStyle = function(applicationModel) {
    var combineFilters = function(filters) {
      return new OpenLayers.Filter.Logical({ type: OpenLayers.Filter.Logical.AND, filters: filters });
    };

    var typeFilter = function(type) {
      return new OpenLayers.Filter.Comparison({ type: OpenLayers.Filter.Comparison.EQUAL_TO, property: 'type', value: type });
    };

    var zoomLevelFilter = function(zoomLevel) {
      return new OpenLayers.Filter.Function({ evaluate: function() { return applicationModel.zoom.level === zoomLevel; } });
    };

    var oneWayFilter = function() {
      return new OpenLayers.Filter.Comparison({ type: OpenLayers.Filter.Comparison.NOT_EQUAL_TO, property: 'sideCode', value: 1 });
    };

    var createZoomAndTypeDependentRule = function(type, zoomLevel, style) {
      return new OpenLayers.Rule({
        filter: combineFilters([typeFilter(type), zoomLevelFilter(zoomLevel)]),
        symbolizer: style
      });
    };

    var createZoomDependentOneWayRule = function(zoomLevel, style) {
      return new OpenLayers.Rule({
        filter: combineFilters([oneWayFilter(), zoomLevelFilter(zoomLevel)]),
        symbolizer: style
      });
    };

    var createZoomAndTypeDependentOneWayRule = function(type, zoomLevel, style) {
      return new OpenLayers.Rule({
        filter: combineFilters([typeFilter(type), oneWayFilter(), zoomLevelFilter(zoomLevel)]),
        symbolizer: style
      });
    };

    var unknownLimitStyleRule = new OpenLayers.Rule({
      filter: typeFilter('unknown'),
      symbolizer: { externalGraphic: 'images/speed-limits/unknown.svg' }
    });

    var overlayStyleRule = _.partial(createZoomAndTypeDependentRule, 'overlay');
    var overlayStyleRules = [
      overlayStyleRule(9, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 6' }),
      overlayStyleRule(10, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 3, strokeDashstyle: '1 10' }),
      overlayStyleRule(11, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 5, strokeDashstyle: '1 15' }),
      overlayStyleRule(12, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
      overlayStyleRule(13, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
      overlayStyleRule(14, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' }),
      overlayStyleRule(15, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' })
    ];

    var oneWayOverlayStyleRule = _.partial(createZoomAndTypeDependentOneWayRule, 'overlay');
    var oneWayOverlayStyleRules = [
      oneWayOverlayStyleRule(9, { strokeDashstyle: '1 6' }),
      oneWayOverlayStyleRule(10, { strokeDashstyle: '1 10' }),
      oneWayOverlayStyleRule(11, { strokeDashstyle: '1 10' }),
      oneWayOverlayStyleRule(12, { strokeDashstyle: '1 16' }),
      oneWayOverlayStyleRule(13, { strokeDashstyle: '1 16' }),
      oneWayOverlayStyleRule(14, { strokeDashstyle: '1 16' }),
      oneWayOverlayStyleRule(15, { strokeDashstyle: '1 16' })
    ];

    var validityDirectionStyleRules = [
      createZoomDependentOneWayRule(9, { strokeWidth: 2 }),
      createZoomDependentOneWayRule(10, { strokeWidth: 4 }),
      createZoomDependentOneWayRule(11, { strokeWidth: 4 }),
      createZoomDependentOneWayRule(12, { strokeWidth: 5 }),
      createZoomDependentOneWayRule(13, { strokeWidth: 5 }),
      createZoomDependentOneWayRule(14, { strokeWidth: 8 }),
      createZoomDependentOneWayRule(15, { strokeWidth: 8 })
    ];

    var speedLimitStyleLookup = {
      20:  { strokeColor: '#00ccdd', externalGraphic: 'images/speed-limits/20.svg' },
      30:  { strokeColor: '#ff55dd', externalGraphic: 'images/speed-limits/30.svg' },
      40:  { strokeColor: '#11bb00', externalGraphic: 'images/speed-limits/40.svg' },
      50:  { strokeColor: '#ff0000', externalGraphic: 'images/speed-limits/50.svg' },
      60:  { strokeColor: '#0011bb', externalGraphic: 'images/speed-limits/60.svg' },
      70:  { strokeColor: '#00ccdd', externalGraphic: 'images/speed-limits/70.svg' },
      80:  { strokeColor: '#ff0000', externalGraphic: 'images/speed-limits/80.svg' },
      90:  { strokeColor: '#ff55dd', externalGraphic: 'images/speed-limits/90.svg' },
      100: { strokeColor: '#11bb00', externalGraphic: 'images/speed-limits/100.svg' },
      120: { strokeColor: '#0011bb', externalGraphic: 'images/speed-limits/120.svg' }
    };

    var speedLimitFeatureSizeLookup = {
      9: { strokeWidth: 3, pointRadius: 0 },
      10: { strokeWidth: 5, pointRadius: 10 },
      11: { strokeWidth: 7, pointRadius: 14 },
      12: { strokeWidth: 10, pointRadius: 16 },
      13: { strokeWidth: 10, pointRadius: 16 },
      14: { strokeWidth: 14, pointRadius: 22 },
      15: { strokeWidth: 14, pointRadius: 22 }
    };

    var typeSpecificStyleLookup = {
      overlay: { strokeOpacity: 1.0 },
      other: { strokeOpacity: 0.7 },
      unknown: { strokeColor: '#000000', strokeOpacity: 0.6, externalGraphic: 'images/speed-limits/unknown.svg' },
      cutter: { externalGraphic: 'images/cursor-crosshair.svg', pointRadius: 11.5 }
    };

    var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults());
    var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });
    browseStyleMap.addUniqueValueRules('default', 'value', speedLimitStyleLookup);
    browseStyleMap.addUniqueValueRules('default', 'level', speedLimitFeatureSizeLookup, applicationModel.zoom);
    browseStyleMap.addUniqueValueRules('default', 'type', typeSpecificStyleLookup);
    browseStyle.addRules(overlayStyleRules);
    browseStyle.addRules(validityDirectionStyleRules);
    browseStyle.addRules(oneWayOverlayStyleRules);

    var selectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.15,
      graphicOpacity: 0.3
    }));
    var selectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      graphicOpacity: 1.0
    }));
    var selectionStyle = new OpenLayers.StyleMap({
      default: selectionDefaultStyle,
      select: selectionSelectStyle
    });
    selectionStyle.addUniqueValueRules('default', 'value', speedLimitStyleLookup);
    selectionStyle.addUniqueValueRules('default', 'level', speedLimitFeatureSizeLookup, applicationModel.zoom);
    selectionStyle.addUniqueValueRules('select', 'type', typeSpecificStyleLookup);
    selectionDefaultStyle.addRules(overlayStyleRules);
    selectionDefaultStyle.addRules(validityDirectionStyleRules);
    selectionDefaultStyle.addRules(oneWayOverlayStyleRules);
    selectionDefaultStyle.addRules([unknownLimitStyleRule]);

    var isUnknown = function(speedLimit) {
      return !_.isNumber(speedLimit.value);
    };

    var lineFeatures = function(speedLimits) {
      return _.map(speedLimits, function(speedLimit) {
        var points = _.map(speedLimit.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var type = isUnknown(speedLimit) ? { type: 'unknown' } : {};
        var attributes = _.merge(_.cloneDeep(speedLimit), type);
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
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
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var road = new OpenLayers.Geometry.LineString(points);
        var signPosition = new GeometryUtils().calculateMidpointOfLineString(road);
        var type = isUnknown(speedLimit) ? { type: 'unknown' } : {};
        var attributes = _.merge(_.cloneDeep(speedLimit), type);
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(signPosition.x, signPosition.y), attributes);
      });
    };

    var renderFeatures = function(speedLimits) {
      var speedLimitsWithType = _.map(speedLimits, function(limit) { return _.merge({}, limit, { type: 'other' }); });
      var offsetBySideCode = function(speedLimit) {
        return LinearAsset().offsetBySideCode(applicationModel.zoom.level, speedLimit);
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
      browsing: browseStyleMap,
      selection: selectionStyle,
      renderFeatures: renderFeatures
    };
  };
})(this);
