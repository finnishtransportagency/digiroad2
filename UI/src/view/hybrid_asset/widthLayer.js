(function(root){
    root.WidthLayer=function (params){
        PointAssetLayer.call(this, params);
        var me = this;

        var application= applicationModel,
            map = params.map,
            mapOverlay = params.mapOverlay,
            roadCollection = params.roadCollection,
            selectedAsset = params.selectedAsset,
            layerName = params.layerName,
            collection = params.collection;

        console.log("load "+params)
        this.getLayerStyle = function(feature)  {
        };

        this.renderOverlays = function(linearAssets) {
        };

        this.renderFeatures = function(linearAssets) {
        };

        this.decorateSelection = function () {

        };
        return {
            vectorLayer: me.vectorLayer,
            show: me.showLayer,
            hide: me.hideLayer,
            minZoomForContent: me.minZoomForContent
        };
    };
})(this);