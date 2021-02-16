(function(root){
    root.OtherLayer=function (params){
        PointAssetLayer.call(this, params);
        var me = this;

        var application= applicationModel,
            map = params.map,
            mapOverlay = params.mapOverlay,
            roadCollection = params.roadCollection,
            selectedAsset = params.selectedAsset,
            layerName = params.layerName,
            collection = params.collection;


        this.getLayerStyle = function(feature)  {
        };

        this.renderOverlays = function(linearAssets) {
        };

        this.renderFeatures = function(linearAssets) {
        };

        this.decorateSelection = function () {

        };

        console.log("load "+params)

        return {
            vectorLayer: me.vectorLayer,
            show: me.showLayer,
            hide: me.hideLayer,
            minZoomForContent: me.minZoomForContent
        };
    };
})(this);