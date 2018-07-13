(function (root) {
  root.PavedRoadLayer = function (params) {
    LinearAssetLayer.call(this, params);
    var me = this;
    var style = params.style;

    this.getLayerStyle = function (feature) {
      return style.browsingStyleProvider.getStyle(feature, {zoomLevel: me.uiState.zoomLevel});
    };

    return {
      vectorLayer: me.vectorLayer,
      show: me.showLayer,
      hide: me.hideLayer,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);