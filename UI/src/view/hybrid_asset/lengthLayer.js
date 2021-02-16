(function(root){
    root.LengthLayer=function (params){
        LinearAssetLayer.call(this, params);
        var me = this;
        var style = params.style,
            collection = params.collection,
            selectedLinearAsset = params.selectedLinearAsset;


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