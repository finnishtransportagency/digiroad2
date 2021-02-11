(function(root){
    root.LengthLayer=function (params){
        LinearAssetLayer.call(this, params);
        var me = this;
        console.log("load")
        eventbus.on("layer2:roadway",function (param) {
            console.log("test event")
        })

        eventbus.on("layer2:roadway1",function (param) {
            console.log("test event 1")
        })

        eventbus.on("layer2:roadway2",function (param) {
            console.log("test event 2")
        })


        return {
            vectorLayer: me.vectorLayer,
            show: me.showLayer,
            hide: me.hideLayer,
            minZoomForContent: me.minZoomForContent
        };
    };
})(this);