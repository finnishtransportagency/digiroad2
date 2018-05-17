(function(root) {
  root.CareClassLayer  = function(params) {
    LinearAssetLayer.call(this, params);
    var me = this;
    var style = params.style,
        collection = params.collection;

    var winterStyle = true;

    this.getLayerStyle = function(feature)  {
      if(winterStyle)
        return style.browsingStyleProvider.getStyle(feature, {zoomLevel: me.uiState.zoomLevel});
      else
        return style.greenCareStyle.getStyle(feature, {zoomLevel: me.uiState.zoomLevel});
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