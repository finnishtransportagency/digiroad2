(function(root) {
  root.WinterSpeedLimitStyle = function() {
    AssetStyle.call(this);
    var me = this;

    var createZoomAndTypeDependentRule = function (type, zoomLevel, style) {
      return new StyleRule().where('type').is(type).and('zoomLevel').is(zoomLevel).use(style);
    };

    var createZoomDependentOneWayRule = function (zoomLevel, style) {
      return new StyleRule().where('sideCode').isNot(1).and('zoomLevel').is(zoomLevel).use(style);
    };

    var createZoomAndTypeDependentOneWayRule = function (type, zoomLevel, style) {
      return new StyleRule().where('type').is(type).and('zoomLevel').is(zoomLevel).and('sideCode').isNot(1).use(style);
    };

    var overlayStyleRule = _.partial(createZoomAndTypeDependentRule, 'overlay');
    var overlayStyleRules = [
      overlayStyleRule(9, {stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 1, lineDash: [1, 6]}}),
      overlayStyleRule(10, {stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 3, lineDash: [1, 10]}}),
      overlayStyleRule(11, {stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 5, lineDash: [1, 15]}}),
      overlayStyleRule(12, {stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 8, lineDash: [1, 22]}}),
      overlayStyleRule(13, {stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 8, lineDash: [1, 22]}}),
      overlayStyleRule(14, {stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 12, lineDash: [1, 28]}}),
      overlayStyleRule(15, {stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 12, lineDash: [1, 28]}})
    ];

    var oneWayOverlayStyleRule = _.partial(createZoomAndTypeDependentOneWayRule, 'overlay');
    var oneWayOverlayStyleRules = [
      oneWayOverlayStyleRule(9, {stroke: {lineDash: [1, 6]}}),
      oneWayOverlayStyleRule(10, {stroke: {lineDash: [1, 10]}}),
      oneWayOverlayStyleRule(11, {stroke: {lineDash: [1, 10]}}),
      oneWayOverlayStyleRule(12, {stroke: {lineDash: [1, 16]}}),
      oneWayOverlayStyleRule(13, {stroke: {lineDash: [1, 16]}}),
      oneWayOverlayStyleRule(14, {stroke: {lineDash: [1, 16]}}),
      oneWayOverlayStyleRule(15, {stroke: {lineDash: [1, 16]}})
    ];

    var validityDirectionStyleRules = [
      createZoomDependentOneWayRule(9, {stroke: {width: 2}}),
      createZoomDependentOneWayRule(10, {stroke: {width: 4}}),
      createZoomDependentOneWayRule(11, {stroke: {width: 4}}),
      createZoomDependentOneWayRule(12, {stroke: {width: 5}}),
      createZoomDependentOneWayRule(13, {stroke: {width: 5}}),
      createZoomDependentOneWayRule(14, {stroke: {width: 8}}),
      createZoomDependentOneWayRule(15, {stroke: {width: 8}})
    ];
    var speedLimitStyleRules = [
      new StyleRule().where('value').is(60).use({ stroke: { color: '#0011bb', fill: '#0011bb'}}),
      new StyleRule().where('value').is(70).use({ stroke: { color: '#00ccdd', fill: '#00ccdd'}}),
      new StyleRule().where('value').is(80).use({ stroke: { color: '#ff0000', fill: '#ff0000'}}),
      new StyleRule().where('value').is(100).use({ stroke: { color: '#11bb00', fill: '#11bb00'}})
    ];

    var speedLimitFeatureSizeRules = [
      new StyleRule().where('zoomLevel').isIn([8 ,9]).use({stroke: {width: 3}, pointRadius: 0}),
      new StyleRule().where('zoomLevel').is(10).use({stroke: {width: 5}, pointRadius: 10}),
      new StyleRule().where('zoomLevel').is(11).use({stroke: {width: 7}, pointRadius: 14}),
      new StyleRule().where('zoomLevel').is(12).use({stroke: {width: 10}, pointRadius: 16}),
      new StyleRule().where('zoomLevel').is(13).use({stroke: {width: 10}, pointRadius: 16}),
      new StyleRule().where('zoomLevel').is(14).use({stroke: {width: 14}, pointRadius: 22}),
      new StyleRule().where('zoomLevel').is(15).use({stroke: {width: 14}, pointRadius: 22})
    ];

    var typeSpecificStyleRules = [
      new StyleRule().where('type').is('overlay').use({stroke: {opacity: 1.0}}),
      new StyleRule().where('type').is('other').use({stroke: {opacity: 0.7}}),
      new StyleRule().where('type').is('unknown').use({stroke: {color: '#7f7f7c', opacity: 0.6}}),
      new StyleRule().where('type').is('cutter').use({icon: {src: 'images/cursor-crosshair.svg'}})
    ];

    var linkTypeSizeRules = [
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).use({ stroke: { width: 6 } }),
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').isIn([10, 9, 8]).use({ stroke: { width: 2 } }),
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').is(11).use({ stroke: { width: 4 } }),
      new StyleRule().where('type').is('overlay').and('linkType').isIn([8, 9, 12, 21]).use({ stroke: {color: '#fff', lineCap: 'square', width: 4, lineDash: [1, 16] } }),
      new StyleRule().where('type').is('overlay').and('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').isIn([10, 9, 8]).use({ stroke: {color: '#fff', lineCap: 'square', width: 1, lineDash: [1, 8] } }),
      new StyleRule().where('type').is('overlay').and('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').is(11).use({ stroke: {color: '#fff', lineCap: 'square', width: 2, lineDash: [1, 8] } })
    ];

    me.browsingStyleProvider = new StyleRuleProvider({});
    me.browsingStyleProvider.addRules(speedLimitStyleRules);
    me.browsingStyleProvider.addRules(speedLimitFeatureSizeRules);
    me.browsingStyleProvider.addRules(typeSpecificStyleRules);
    me.browsingStyleProvider.addRules(overlayStyleRules);
    me.browsingStyleProvider.addRules(validityDirectionStyleRules);
    me.browsingStyleProvider.addRules(oneWayOverlayStyleRules);
    me.browsingStyleProvider.addRules(linkTypeSizeRules);

    this.renderOverlays = function(linearAssets){
      var speedLimitsWithType = _.map(linearAssets, function(linearAsset) { return _.merge({}, linearAsset, { type: 'other' }); });
      var offsetBySideCode = function(linearAsset) {
        return GeometryUtils.offsetBySideCode(applicationModel.zoom.level, linearAsset);
      };
      var speedLimitsWithAdjustments = _.map(speedLimitsWithType, offsetBySideCode);
      var speedLimitsSplitAt70kmh = _.groupBy(speedLimitsWithAdjustments, function(linearAsset) { return linearAsset.value >= 70; });
      return me.dottedLineFeatures(speedLimitsSplitAt70kmh[true]).concat(me.limitSigns(speedLimitsWithAdjustments));
    };

    this.limitSigns = function(speedLimits) {
      return _.map(speedLimits, function(speedLimit) {
        var points = _.map(speedLimit.points, function(point) {
          return [point.x, point.y];
        });
        var road = new ol.geom.LineString(points);
        var signPosition = GeometryUtils.calculateMidpointOfLineString(road);
        var type = me.isUnknown(speedLimit) ? { type: 'unknown' } : {};
        var attributes = _.merge(_.cloneDeep(_.omit(speedLimit, "geometry")), type);

        var feature = new ol.Feature(new ol.geom.Point([signPosition.x, signPosition.y]));
        feature.setProperties(attributes);
        return feature;
      });
    };

    this.dottedLineFeatures = function(linearAssets) {
      var solidLines = me.lineFeatures(linearAssets);
      var dottedOverlay = me.lineFeatures(_.map(linearAssets, function(linearAsset) { return _.merge({}, linearAsset, { type: 'overlay' }); }));
      return solidLines.concat(dottedOverlay);
    };

    this.isUnknown = function(linearAsset) {
      return !_.isNumber(linearAsset.value);
    };

    me.renderFeatures = function(linearAssets) {
      return me.lineFeatures(me.getNewFeatureProperties(linearAssets)).concat(me.renderOverlays(linearAssets));
    };

  };
})(this);
