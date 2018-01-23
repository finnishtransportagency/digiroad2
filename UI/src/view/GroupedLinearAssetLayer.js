(function (root) {
  root.GroupedLinearAssetLayer = function (params, map) {

    var isActive = params.massLimitation;
    var style = params.style;
    var collection = params.collection;
    var typeId = params.typeId;
    var isComplementaryChecked = false;

    var uiState = { zoomLevel: 9 };

    var vectorSource = new ol.source.Vector();
    var vectorLayer = new ol.layer.Vector({
      source : vectorSource,
      style : function(feature) {
        return style.browsingStyleProviderReadOnly.getStyle(feature, {zoomLevel: uiState.zoomLevel});
      }
    });

    vectorLayer.set('name', 'GroupedLinearAssetLayer');
    vectorLayer.setOpacity(1);
    vectorLayer.setVisible(false);
    map.addLayer(vectorLayer);

    var adjustStylesByZoomLevel = function(zoom) {
      uiState.zoomLevel = zoom;
    };

    var showLayer = function(){
      if(isActive)
        vectorLayer.setVisible(true);
    };

    var hideLayer = function(){
      vectorLayer.setVisible(false);
    };

    var showWithComplementary = function () {
      isComplementaryChecked = true;
    };

    var hideComplementary = function(){
      isComplementaryChecked = false;
    };

    var removeLayerFeatures = function(){
      vectorLayer.getSource().clear();
    };

    var redrawLinearAssets = function(linearAssetChains) {
      vectorSource.clear();
      var linearAssets = _.flatten(linearAssetChains);
      drawLinearAssets(linearAssets);
    };

    var offsetBySideCode = function (linearAsset) {
      return GeometryUtils.offsetBySideCode(applicationModel.zoom.level, linearAsset);
    };

    var drawLinearAssets = function(linearAssets) {
      var asset = _.filter(linearAssets, function(asset) { return !_.some(asset.values, function(type) { return type.typeId == typeId; }); });
      vectorSource.addFeatures(new MassLimitationsLabel().renderFeaturesByLinearAssets(_.map(_.cloneDeep(linearAssets), offsetBySideCode), uiState.zoomLevel));
      vectorSource.addFeatures(style.renderFeatures(asset));
    };

    var refreshView = function () {
      vectorLayer.setVisible(true);
      adjustStylesByZoomLevel(map.getView().getZoom());
      if(isComplementaryChecked) {
        collection.fetchReadOnlyAssetsWithComplementary(map.getView().calculateExtent(map.getSize())).then(function () {
          eventbus.trigger('layer:readOnlyLayer:' + event);
        });
      }
      else {
        collection.fetchReadOnlyAssets(map.getView().calculateExtent(map.getSize())).then(function () {
          eventbus.trigger('layer:readOnlyLayer:' + event);
        });
      }
    };
    eventbus.on('fetchedReadOnly', redrawLinearAssets);

    return {
      refreshView: refreshView,
      redrawLinearAssets: redrawLinearAssets,
      hideLayer: hideLayer,
      showLayer: showLayer,
      removeLayerFeatures: removeLayerFeatures,
      showWithComplementary: showWithComplementary,
      hideComplementary: hideComplementary
    };
  };
})(this);
