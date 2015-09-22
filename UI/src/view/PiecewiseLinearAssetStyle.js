(function(root) {
  root.PiecewiseLinearAssetStyle = function(applicationModel) {
    var singleElementEvents = function() {
      return _.map(arguments, function(argument) { return singleElementEventCategory + ':' + argument; }).join(' ');
    };

    var multiElementEvent = function(eventName) {
      return multiElementEventCategory + ':' + eventName;
    };

    var combineFilters = function(filters) {
      return new OpenLayers.Filter.Logical({ type: OpenLayers.Filter.Logical.AND, filters: filters });
    };

    var zoomLevelFilter = function(zoomLevel) {
      return new OpenLayers.Filter.Function({ evaluate: function() { return applicationModel.zoom.level === zoomLevel; } });
    };

    var oneWayFilter = function() {
      return new OpenLayers.Filter.Comparison({ type: OpenLayers.Filter.Comparison.NOT_EQUAL_TO, property: 'sideCode', value: 1 });
    };

    var createZoomDependentOneWayRule = function(zoomLevel, style) {
      return new OpenLayers.Rule({
        filter: combineFilters([oneWayFilter(), zoomLevelFilter(zoomLevel)]),
        symbolizer: style
      });
    };

    var validityDirectionStyleRules = [
      createZoomDependentOneWayRule(9, { strokeWidth: 2 }),
      createZoomDependentOneWayRule(10, { strokeWidth: 4 }),
      createZoomDependentOneWayRule(11, { strokeWidth: 4 }),
      createZoomDependentOneWayRule(12, { strokeWidth: 8 }),
      createZoomDependentOneWayRule(13, { strokeWidth: 8 }),
      createZoomDependentOneWayRule(14, { strokeWidth: 8 }),
      createZoomDependentOneWayRule(15, { strokeWidth: 8 })
    ];

    var styleLookup = {
      false: {strokeColor: '#ff0000'},
      true: {strokeColor: '#7f7f7c'}
    };

    var typeSpecificStyleLookup = {
      line: { strokeOpacity: 0.7 },
      cutter: { externalGraphic: 'images/cursor-crosshair.svg', pointRadius: 11.5 }
    };

    var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults());
    var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });
    browseStyleMap.addUniqueValueRules('default', 'expired', styleLookup);
    browseStyleMap.addUniqueValueRules('default', 'level', RoadLayerSelectionStyle.linkSizeLookup, applicationModel.zoom);
    browseStyleMap.addUniqueValueRules('default', 'type', typeSpecificStyleLookup);
    browseStyle.addRules(validityDirectionStyleRules);

    var selectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.15
    }));
    var selectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7
    }));
    var selectionStyle = new OpenLayers.StyleMap({
      default: selectionDefaultStyle,
      select: selectionSelectStyle
    });
    selectionStyle.addUniqueValueRules('default', 'expired', styleLookup);
    selectionStyle.addUniqueValueRules('default', 'level', RoadLayerSelectionStyle.linkSizeLookup, applicationModel.zoom);
    selectionStyle.addUniqueValueRules('select', 'type', typeSpecificStyleLookup);
    selectionDefaultStyle.addRules(validityDirectionStyleRules);

    var lineFeatures = function(linearAssets) {
      return _.flatten(_.map(linearAssets, function(linearAsset) {
        var points = _.map(linearAsset.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), linearAsset);
      }));
    };

    var renderFeatures = function(linearAssets) {
      var linearAssetsWithType = _.map(linearAssets, function(limit) {
        var expired = _.isUndefined(limit.id) || limit.expired;
        return _.merge({}, limit, { type: 'line', expired: expired + '' });
      });
      var offsetBySideCode = function(linearAsset) {
        return LinearAsset().offsetBySideCode(applicationModel.zoom.level, linearAsset);
      };
      var linearAssetsWithAdjustments = _.map(linearAssetsWithType, offsetBySideCode);
      return lineFeatures(linearAssetsWithAdjustments);
    };

    return {
      browsing: browseStyleMap,
      selection: selectionStyle,
      renderFeatures: renderFeatures
    };
  };
})(this);

