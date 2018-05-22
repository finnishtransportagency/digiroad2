(function(root) {
    root.ServiceRoadLayer  = function(params) {
        LinearAssetLayer.call(this, params);
        var me = this;
        var style = params.style,
            collection = params.collection;

        var isResponsibilityTypeStyle = true;

        this.getLayerStyle = function(feature)  {
            if(isResponsibilityTypeStyle)
                return style.browsingStyleProvider.getStyle(feature, {zoomLevel: me.uiState.zoomLevel});
            else
                return style.rightOfUseStyle.getStyle(feature, {zoomLevel: me.uiState.zoomLevel});
        };

        eventbus.on('serviceRoad:responsibility', function(value) {
          isResponsibilityTypeStyle = value;
            eventbus.trigger('maintenanceRoads:fetched', collection.getAll());
        });

        return {
            vectorLayer: me.vectorLayer,
            show: me.showLayer,
            hide: me.hideLayer,
            minZoomForContent: me.minZoomForContent
        };
    };
})(this);