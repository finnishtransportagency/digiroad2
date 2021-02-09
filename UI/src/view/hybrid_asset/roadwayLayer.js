(function(root) {
  root.RoadwayLayer  = function(params) {
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

(function(root){
  root.LengthLayer=function (params){
    LinearAssetLayer.call(this, params);
    var me = this;

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

(function(root){
  root.WidthLayer=function (params){
    PointAssetLayer.call(this, params);
    var me = this;
    return {
      vectorLayer: me.vectorLayer,
      show: me.showLayer,
      hide: me.hideLayer,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);

(function(root){
  root.OtherLayer=function (params){
    PointAssetLayer.call(this, params);
    var me = this;
    return {
      vectorLayer: me.vectorLayer,
      show: me.showLayer,
      hide: me.hideLayer,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);