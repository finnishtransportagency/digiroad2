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
    var selectToolControl = me.getSelectToolControl();
    var vectorSource = me.getVectorSource();

    function hasAsset(linearAsset) {
      var id = linearAsset.id;
      var value = linearAsset.value;
      var nullOrUndefined = function(id){return _.isUndefined(id) || _.isNull(id);};
      var hasValueProperty = function(){return linearAsset.hasOwnProperty('value');};

      return (nullOrUndefined(id) && !_.isUndefined(value)) || (!nullOrUndefined(id) && !hasValueProperty()) ||  (!nullOrUndefined(id) && !_.isUndefined(value));
    }

    var valueExists = function(asset, publicId) {
      return !_.isUndefined(asset.value) && !emptyValues(asset, publicId);
    };

    var findValue = function(asset, publicId) {
      var properties = _.find(asset.value.properties, function(a) { return a.publicId === publicId; });
      if(properties)
        return _.first(properties.values).value;
    };

    var emptyValues = function(asset, publicId) {
      var properties = _.find(asset.value.properties, function(a) { return a.publicId === publicId; });
      return properties ?  !_.isUndefined(asset.id) && _.isEmpty(properties.values): !_.isUndefined(asset.id) ;
    };

    var offsetBySideCode = function (linearAsset) {
      return GeometryUtils.offsetBySideCode(applicationModel.zoom.level, linearAsset);
    };

    var pointFeatures = function(linearAssets){
      return _.map(linearAssets,  function(linearAsset) {
        var points = _.map(linearAsset.points, function(point) {
          return [point.x, point.y];
        });
        var hasAsset = hasAsset(linearAsset);
        var road = new ol.geom.LineString(points);
        var signPosition = GeometryUtils.calculateMidpointOfLineString(road);
        var noGreenCare = hasAsset && !valueExists(linearAsset, greenCareClass);
        var noWinterCare = hasAsset && !valueExists(linearAsset, winterCareClass);
        var properties = _.merge(linearAsset, {noGreenCare: noGreenCare}, {noWinterCare: noWinterCare}, { hasAsset: hasAsset });
        var feature = new ol.Feature(new ol.geom.Point([signPosition.x, signPosition.y]));
        feature.setProperties(_.omit(properties, 'geometry'));
        return feature;
      });
    };

    var lineFeatures = function(linearAssets) {
      return _.map(linearAssets, function(linearAsset) {
        var points = _.map(linearAsset.points, function(point) {
          return [point.x, point.y];
        });
        var noGreenCare = !_.isUndefined(linearAsset.id) && !valueExists(linearAsset, greenCareClass);
        var noWinterCare = !_.isUndefined(linearAsset.id) && !valueExists(linearAsset, winterCareClass);
        var hasAsset = hasAsset(linearAsset);

        var properties = _.merge(linearAsset, {noGreenCare: noGreenCare}, {noWinterCare: noWinterCare}, { hasAsset: hasAsset });
        var feature = new ol.Feature(new ol.geom.LineString(points));
        feature.setProperties(_.omit(properties, 'geometry'));
        return feature;
      });
    };

    this.getLayerStyle = function(feature)  {
      if(winterStyle)
        return style.browsingStyleProvider.getStyle(feature, {zoomLevel: me.uiState.zoomLevel});
      else
        return style.greenCareStyle.getStyle(feature, {zoomLevel: me.uiState.zoomLevel});
    };

    this.renderOverlays = function(linearAssets) {
      return lineFeatures(_.map(_.filter(linearAssets, function (asset){return asset.value && !emptyValues(asset, winterCareClass) && _.includes(overlayAssets, parseInt(findValue(asset, winterCareClass)));}), function(linearAsset) {
        return _.merge({}, linearAsset, { type: 'overlay' }); }));
    };

    this.renderFeatures = function(linearAssets) {
      return lineFeatures(style.getNewFeatureProperties(linearAssets)).concat(me.renderOverlays(linearAssets)).concat(pointFeatures(linearAssets));
    };

    this.drawLinearAssets = function(linearAssets) {
      vectorSource.addFeatures(me.renderFeatures(linearAssets));
    };

    this.highlightMultipleLinearAssetFeatures = function() {
      var selectedAssets = selectedLinearAsset.get();
      var features = me.renderFeatures(selectedAssets);
      selectToolControl.addSelectionFeatures(features);
    };

    this.decorateSelection = function () {
      if (selectedLinearAsset.exists()) {
        var features = me.renderFeatures(selectedLinearAsset.get());
        selectToolControl.addSelectionFeatures(features);
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