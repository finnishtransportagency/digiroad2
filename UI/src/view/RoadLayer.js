var RoadStyles = function() {

  var administrativeClassStyleRule = [
    new StyleRule().where('administrativeClass').is('Private').use({ stroke: {color: '#0011bb' }}),
    new StyleRule().where('administrativeClass').is('Municipality').use({ stroke: {color: '#11bb00' }}),
    new StyleRule().where('administrativeClass').is('State').use({ stroke: {color: '#ff0000' }}),
    new StyleRule().where('administrativeClass').is('Unknown').use({ stroke: {color: '#888' }})
  ];

  return {
    provider: function () {
        var defaultProvider = new StyleRuleProvider({ stroke: {width:5 , opacity: 0.7, color: "#a4a4a2" }});
        var selectionProvider = new StyleRuleProvider({ stroke: {width: 6, opacity: 1, color: "#5eaedf" }});

        if (applicationModel.isRoadTypeShown()){
            defaultProvider.addRules(administrativeClassStyleRule);
            return {
              default: defaultProvider,
              selection: selectionProvider
            };
        }
        return {
            default: defaultProvider,
            selection: selectionProvider
        };
    }
  };
};

(function(root) {
  root.RoadLayer = function(map, roadCollection,styler) {
    var vectorLayer;
    var roadTypeWithSpecifiedStyle = false;
    var roadStyle = new RoadStyles();
    var layerMinContentZoomLevels = {};
    var layerStyleProviders = {};
    var uiState = { zoomLevel: 9 };
    var enabled = true;

    var selectControl = new ol.interaction.Select({
        layers : [vectorLayer],
        condition: function(events){
          return enabled &&ol.events.condition.singleClick(events);
        },
        style : function(feature) {
           return roadStyle.provider().selection.getStyle(feature, {zoomLevel: uiState.zoomLevel});
        }
    });
    map.addInteraction(selectControl);

    var vectorSource = new ol.source.Vector({
      strategy: ol.loadingstrategy.bbox
    });

    var setZoomLevel = function(zoom){
      uiState.zoomLevel = zoom;
    };

    var getZoomLevel = function(){
      return uiState.zoomLevel;
    };

    var clear = function() {
      vectorSource.clear();
    };

    var createRoadLinkFeature = function(roadLink){
      var points = _.map(roadLink.points, function(point) {
        return [point.x, point.y];
      });
      return new ol.Feature(_.merge({}, roadLink, { geometry: new ol.geom.LineString(points)}));
    };

    var drawRoadLinks = function(roadLinks, zoom) {
      setZoomLevel(zoom);
      eventbus.trigger('roadLinks:beforeDraw');
      vectorSource.clear();
      var features = _.map(roadLinks, function(roadLink) {
        return createRoadLinkFeature(roadLink);
      });
      vectorSource.addFeatures(features);
      eventbus.trigger('roadLinks:afterDraw', roadLinks);
    };

    var drawRoadLink = function(roadLink){
      var feature = createRoadLinkFeature(roadLink);
      vectorSource.addFeatures(feature);
    };

    var setLayerSpecificStyleProvider = function(layer, provider) {
      layerStyleProviders[layer] = provider;
    };

    var setLayerSpecificMinContentZoomLevel = function(layer, zoomLevel) {
      layerMinContentZoomLevels[layer] = zoomLevel;
    };

    function getStyleProvider(){
      if(stylesUndefined() || roadTypeWithSpecifiedStyle)
        return roadStyle.provider().default;

      var currentLayerProvider = layerStyleProviders[applicationModel.getSelectedLayer()]();

      if(currentLayerProvider.default)
        return currentLayerProvider.default;

      return currentLayerProvider;
    }

    function vectorLayerStyle(feature) {
      return getStyleProvider().getStyle(feature, {zoomLevel: uiState.zoomLevel});
    }

    function toggleRoadTypeWithSpecifiedStyle(){
      roadTypeWithSpecifiedStyle = !roadTypeWithSpecifiedStyle;
      vectorSource.changed();
    }

    function stylesUndefined() {
      return _.isUndefined(layerStyleProviders[applicationModel.getSelectedLayer()]);
    }

    var minimumContentZoomLevel = function() {
      if (!_.isUndefined(layerMinContentZoomLevels[applicationModel.getSelectedLayer()])) {
        return layerMinContentZoomLevels[applicationModel.getSelectedLayer()];
      }
      return zoomlevels.minZoomForRoadLinks;
    };

    var handleRoadsVisibility = function() {
      if (_.isObject(vectorLayer)) {
        vectorLayer.setVisible(map.getView().getZoom() >= minimumContentZoomLevel());
      }
    };

    var mapMovedHandler = function(mapState) {
      if (mapState.zoom < minimumContentZoomLevel()) {
        vectorSource.clear();
        roadCollection.reset();
      }
      handleRoadsVisibility();
    };

    vectorLayer = new ol.layer.Vector({
      source: vectorSource,
      style: vectorLayerStyle
    });

    vectorLayer.set('name', 'road');
    vectorLayer.setVisible(true);

    map.addLayer(vectorLayer);

    var selectRoadLink = function(roadLink) {
      var feature = _.find(vectorLayer.getSource().getFeatures(), function(feature) {
          if (roadLink.linkId) return feature.getProperties().linkId === roadLink.linkId;
          else return feature.getProperties().roadLinkId === roadLink.roadLinkId;
      });

      var featureClone = feature.clone();
      featureClone.setId(feature.getId());

      addSelectionFeatures(featureClone);
    };

    var clearSelection = function(){
      selectControl.getFeatures().clear();
    };

    var addSelectionFeatures = function(feature){
      clearSelection();
      selectControl.getFeatures().push(feature);
    };

    var deactivateSelection = function() {
      enabled = false;
    };

    var activateSelection = function() {
      enabled = true;
    };

    eventbus.on('map:moved', mapMovedHandler, this);

    return {
      deactivateSelection: deactivateSelection,
      activateSelection: activateSelection,
      createRoadLinkFeature: createRoadLinkFeature,
      setLayerSpecificMinContentZoomLevel: setLayerSpecificMinContentZoomLevel,
      setLayerSpecificStyleProvider: setLayerSpecificStyleProvider,
      drawRoadLink: drawRoadLink,
      drawRoadLinks: drawRoadLinks,
      selectRoadLink: selectRoadLink,
      clear: clear,
      clearSelection: clearSelection,
      getZoomLevel: getZoomLevel,
      layer: vectorLayer,
      toggleRoadTypeWithSpecifiedStyle: toggleRoadTypeWithSpecifiedStyle
    };
  };
})(this);
