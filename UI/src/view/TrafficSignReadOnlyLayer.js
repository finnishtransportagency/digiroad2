(function (root) {
  root.TrafficSignReadOnlyLayer = function(params) {
    var allowGrouping = true,
      parentLayerName = params.layerName,
      style = params.style,
      assetLabel = params.assetLabel,
      collection = params.collection,
      assetGrouping = params.assetGrouping,
      map = params.map;

    var me = this;
    me.minZoomForContent = zoomlevels.minZoomForAssets;
    var vectorSource = new ol.source.Vector();
    var vectorLayer = new ol.layer.Vector({
      source: vectorSource,
      style: function (feature) {
        return style.browsingStyleProvider.getStyle(feature);
      },
      renderBuffer: 0
    });

    var isShowingForLayer = {
      speedLimit: false,
      trSpeedLimits: false,
      totalWeightLimit: false,
      trailerTruckWeightLimit: false,
      axleWeightLimit: false,
      bogieWeightLimit: false,
      heightLimit: false,
      lengthLimit: false
    };

    var setLayerToShow = function(layerName, isShowing){
      isShowingForLayer[layerName] = isShowing;
    };

    vectorLayer.set('name', 'trafficSignReadOnly' + parentLayerName);
    vectorLayer.setOpacity(1);
    vectorLayer.setVisible(false);
    vectorLayer.setZIndex(map.getLayers().getArray().length);
    map.addLayer(vectorLayer);

    var showReadOnlyTrafficSigns = function(){
      setLayerToShow(parentLayerName, true);
      collection.setTrafficSigns(parentLayerName, true);
      me.show();
      me.refreshView();
    };

    var hideReadOnlyTrafficSigns = function(){
      setLayerToShow(parentLayerName, false);
      collection.setTrafficSigns(parentLayerName, false);
      me.hide();
    };

    this.unHighLightLayer = function(){
      vectorLayer.setOpacity(0.15);
    };

    this.highLightLayer = function(){
      vectorLayer.setOpacity(1);
    };

    eventbus.on(parentLayerName + ':hideReadOnlyTrafficSigns', hideReadOnlyTrafficSigns);
    eventbus.on(parentLayerName + ':showReadOnlyTrafficSigns', showReadOnlyTrafficSigns);
    eventbus.on('readOnlyLayer:' + parentLayerName + ':shown', function (layerName) {
       showLayer(layerName);
    }, this);

    var showLayer = function(layer){
      if(layer == parentLayerName && isShowingForLayer[parentLayerName]){
        me.show();
        me.refreshView();
      }
    };

    this.refreshView = function () {
      collection.fetch(map.getView().calculateExtent(map.getSize())).then(function (assets) {
          var features = (!allowGrouping) ? _.map(assets, createFeature) : getGroupedFeatures(assets);
          vectorLayer.getSource().addFeatures(features);
          vectorLayer.getSource().addFeatures(assetLabel.renderFeaturesByPointAssets(assets, map.getView().getZoom()));
        });
    };

    this.removeLayerFeatures = function() {
      vectorLayer.getSource().clear();
    };

    var getGroupedFeatures = function (assets) {
      var assetGroups = assetGrouping.groupByDistance(assets, map.getView().getZoom());
      var modifiedAssets = _.forEach(assetGroups, function (assetGroup) {
        _.map(assetGroup, function (asset) {
          asset.lon = _.head(assetGroup).lon;
          asset.lat = _.head(assetGroup).lat;
        });
      });
      return _.map(_.flatten(modifiedAssets), createFeature);
    };

    this.showTrafficSignsComplementary = function() {
      collection.activeComplementary(true);
      me.refreshView();
    };

    this.hideTrafficSignsComplementary = function() {
      me.removeLayerFeatures();
      collection.activeComplementary(false);
      me.refreshView();
    };

    function createFeature(asset) {
      var rotation = determineRotation(asset);
      var bearing = determineBearing(asset);
      var feature =  new ol.Feature({geometry : new ol.geom.Point([asset.lon, asset.lat])});
      var obj = _.merge({}, asset, {rotation: rotation, bearing: bearing}, feature.getProperties());
      feature.setProperties(obj);
      return feature;
    }

    function determineRotation(asset) {
      return validitydirections.calculateRotation(asset.bearing, asset.validityDirection);
    }

    function determineBearing(asset) {
      return asset.bearing;
    }

    this.show = function() {
      vectorLayer.setVisible(true);
    };

    this.hide = function() {
      vectorLayer.setVisible(false);
    };
  };
})(this);

