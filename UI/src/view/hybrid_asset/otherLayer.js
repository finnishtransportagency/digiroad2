(function(root){
    root.OtherLayer=function (params){
        PointAssetLayer.call(this, params);

        console.log("load")
        var me = this;
        return {
            vectorLayer: me.vectorLayer,
            show: me.showLayer,
            hide: me.hideLayer,
            minZoomForContent: me.minZoomForContent
        };
    };
})(this);