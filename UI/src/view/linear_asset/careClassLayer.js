(function(root) {
  root.CareClassLayer  = function(params) {
    LinearAssetLayer.call(this, params);
    var me = this;
    var style = params.style,
      collection = params.collection,
      selectedLinearAsset = params.selectedLinearAsset;

    var greenCareClass = 'hoitoluokat_viherhoitoluokka';
    var winterCareClass = 'hoitoluokat_talvihoitoluokka';
    var overlayAssets = [20, 30, 40, 50, 60, 70];
    var winterStyle = true;

    var valueExists = function(asset, publicId) {
      return !_.isUndefined(asset.value) && !emptyValues(asset, publicId);
    };

    var findValue = function(asset, publicId) {
      return _.first(_.find(asset.value.properties, function(a) { return a.publicId === publicId; }).values).value;
    };

    var emptyValues = function(asset, publicId) {
      return !_.isUndefined(asset.id) && _.isEmpty(_.find(asset.value.properties, function(a) { return a.publicId === publicId; }).values);
    };

    var groupDashedAndSolid = function(linearAssets) {
      return _.groupBy(linearAssets, function (asset) {
        return asset.value && !emptyValues(asset, winterCareClass) && _.contains(overlayAssets, parseInt(findValue(asset, winterCareClass)));

      });
    };

    var lineFeatures = function(linearAssets) {
      return _.map(linearAssets, function(linearAsset) {
        var points = _.map(linearAsset.points, function(point) {
          return [point.x, point.y];
        });
        var noGreenCare = !_.isUndefined(linearAsset.id) && !valueExists(linearAsset, greenCareClass);
        var noWinterCare = !_.isUndefined(linearAsset.id) && !valueExists(linearAsset, winterCareClass);
        var properties = _.merge(_.cloneDeep(linearAsset), {noGreenCare: noGreenCare}, {noWinterCare: noWinterCare});
        var feature = new ol.Feature(new ol.geom.LineString(points));
        feature.setProperties(properties);
        return feature;
      });
    };

    this.getLayerStyle = function(feature)  {
      if(winterStyle)
        return style.browsingStyleProvider.getStyle(feature, {zoomLevel: me.uiState.zoomLevel});
      else
        return style.greenCareStyle.getStyle(feature, {zoomLevel: me.uiState.zoomLevel});
    };

    this.addUnknown = function(linearAssets){
      return _.map(linearAssets, function(asset) {
        var points = _.map(asset.points, function(point) {
          return [point.x, point.y];
        });
        var road = new ol.geom.LineString(points);
        var signPosition = GeometryUtils.calculateMidpointOfLineString(road);
        var noGreenCare = !_.isUndefined(asset.id) && !valueExists(asset, greenCareClass);
        var noWinterCare = !_.isUndefined(asset.id) && !valueExists(asset, winterCareClass);
        var properties = _.merge(_.cloneDeep(asset), {noGreenCare: noGreenCare}, {noWinterCare: noWinterCare});
        var feature = new ol.Feature(new ol.geom.Point([signPosition.x, signPosition.y]));
        feature.setProperties(_.omit(properties, 'geometry'));
        return feature;
      });
    };

    var dashedOverlay = function(linearAssets) {
      var solidLines = lineFeatures(linearAssets);
      var dottedOverlay = lineFeatures(_.map(linearAssets, function(limit) { return _.merge({}, limit, { overlay: true}); }));
      return solidLines.concat(dottedOverlay);
    };

    this.drawLinearAssets = function(linearAssets, vectorSource) {
      var existingAssets = _.groupBy(linearAssets, function(asset){return !_.isUndefined(asset.id);});
      var dashedAndSolid = groupDashedAndSolid(existingAssets[true]);
      var dashed = dashedAndSolid[true];
      var solid = dashedAndSolid[false];
      vectorSource.addFeatures(style.renderFeatures(existingAssets[false]));
      vectorSource.addFeatures(me.addUnknown(linearAssets));
      vectorSource.addFeatures(dashedOverlay(dashed));
      vectorSource.addFeatures(lineFeatures(solid));
    };

    this.decorateSelection = function (selectToolControl) {
      if (selectedLinearAsset.exists()) {
        var assets = selectedLinearAsset.get();
        var existingAssets = _.groupBy(assets, function(asset){return !_.isUndefined(asset.value);});
        var dashedAndSolid = groupDashedAndSolid(existingAssets[true]);
        var dashed = dashedOverlay(dashedAndSolid[true]);
        var solid = lineFeatures(dashedAndSolid[false]);
        var selectedFeatures = _.flatten(_.reject([dashed, solid], function(selectedAssets){return _.isEmpty(selectedAssets);}));
        selectToolControl.addSelectionFeatures(selectedFeatures);

        if (selectedLinearAsset.isSplitOrSeparated()) {
          me.drawIndicators(_.map(_.cloneDeep(selectedLinearAsset.get()), offsetBySideCode));
        }
      }
    };

    eventbus.on('careClass:winterCare', function(value) {
      winterStyle = value;
      eventbus.trigger('careClasses:fetched', collection.getAll());
    });

    return {
      vectorLayer: me.vectorLayer,
      show: me.showLayer,
      hide: me.hideLayer,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);