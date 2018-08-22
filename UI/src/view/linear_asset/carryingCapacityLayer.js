(function(root) {
    root.CarryingCapacityLayer  = function(params) {
        LinearAssetLayer.call(this, params);
        var me = this;
        var style = params.style,
            collection = params.collection;

        var springCarryingCapacityStyle = true;

        this.getLayerStyle = function(feature)  {
            if(springCarryingCapacityStyle)
                return style.springCarryingCapacityStyle.getStyle(feature, {zoomLevel: me.uiState.zoomLevel});
            else
                return style.frostHeavingFactorStyle.getStyle(feature, {zoomLevel: me.uiState.zoomLevel});
        };

        eventbus.on(params.singleElementEventCategory +':spring-carrying-capacity', function(value) {
          springCarryingCapacityStyle = value;
            eventbus.trigger(params.singleElementEventCategory + ':fetched', collection.getAll());
        });

        return {
            vectorLayer: me.vectorLayer,
            show: me.showLayer,
            hide: me.hideLayer,
            minZoomForContent: me.minZoomForContent
        };
    };
})(this);