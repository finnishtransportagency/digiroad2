var RoadStyles = function() {

  var administrativeClassStyleRule = [
    new StyleRule().where('administrativeClass').is('Private').use({ stroke: {color: '#0011bb' }}),
    new StyleRule().where('administrativeClass').is('Municipality').use({ stroke: {color: '#11bb00' }}),
    new StyleRule().where('administrativeClass').is('State').use({ stroke: {color: '#ff0000' }}),
    new StyleRule().where('administrativeClass').is('Unknown').use({ stroke: {color: '#888' }})
  ];

  //var selectionStyle = [new StyleRule().use({ stroke: {width: 6, opacity: 1, color: "#5eaedf" }})];
  return {
    provider: function () {
        //TODO: Remove this and give support to defaults
        var defaultProvider = new StyleRuleProvider({ stroke: {width:5 , opacity: 0.7, color: "#a4a4a2" }});
        var selectionProvider = new StyleRuleProvider({ stroke: {width: 6, opacity: 1, color: "#5eaedf" }});
        //selectionProvider.addRules(selectionStyle);

        if (applicationModel.isRoadTypeShown()){
            defaultProvider.addRules(administrativeClassStyleRule);
            return {
              defaultStyleProvider: defaultProvider,
              selectionStyleProvider: selectionProvider
            };
        }
        return {
            defaultStyleProvider: defaultProvider,
            selectionStyleProvider: selectionProvider
        };
    }
  };
};

(function(root) {
  root.RoadLayer3 = function(map, roadCollection,styler) {
    var vectorLayer;
    var layerMinContentZoomLevels = {};
    var layerStyleProviders = {};
    var uiState = { zoomLevel: 9 };
    var enabled = true;

    var selectControl = new ol.interaction.Select({
        layers : [vectorLayer],
        //TODO: Review this style application
        condition: function(events){
          return enabled &&ol.events.condition.singleClick(events);
        },
        style : function(feature) {
           return (new RoadStyles()).provider().selectionStyleProvider.getStyle(feature, {zoomLevel: uiState.zoomLevel});
        }
    });
    map.addInteraction(selectControl);
    var vectorSource = new ol.source.Vector({
      //loader: function(extent, resolution, projection) {
      //  var zoom = Math.log(1024/resolution) / Math.log(2);
      //  //setZoomLevel(zoom);
      //
      //
      //  eventbus.once('roadLinks:fetched', function() {
      //    vectorSource.clear();
      //    var features = _.map(roadCollection.getAll(), function(roadLink) {
      //      var points = _.map(roadLink.points, function(point) {
      //        return [point.x, point.y];
      //      });
      //      return new ol.Feature(_.merge({},roadLink, { geometry: new ol.geom.LineString(points)}));
      //      //feature.roadLinkData = roadLink;
      //      //return feature;
      //    });
      //    vectorSource.addFeatures(features);
      //  });
      //
      //  roadCollection.fetch(extent.join(','), zoom);
      //},
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

    //TODO maybe it's a good idea to spearete draw road links and set zoom
    var drawRoadLinks = function(roadLinks, zoom) {
      setZoomLevel(zoom);
      eventbus.trigger('roadLinks:beforeDraw');
      vectorSource.clear();
      var features = _.map(roadLinks, function(roadLink) {
        return createRoadLinkFeature(roadLink);
      });
      usingLayerSpecificStyleProvider(function() {
        vectorSource.addFeatures(features);
      });
      eventbus.trigger('roadLinks:afterDraw', roadLinks);
    };

    var drawRoadLink = function(roadLink){
      var feature = createRoadLinkFeature(roadLink);
      usingLayerSpecificStyleProvider(function() {
        vectorSource.addFeatures(feature);
      });
    };

    var setLayerSpecificStyleProvider = function(layer, provider) {
      layerStyleProviders[layer] = provider;
    };

    var setLayerSpecificMinContentZoomLevel = function(layer, zoomLevel) {
      layerMinContentZoomLevels[layer] = zoomLevel;
    };

    function vectorLayerStyle(feature, resolution) {
      if(stylesUndefined())
          return (new RoadStyles()).provider().defaultStyleProvider.getStyle(feature, {zoomLevel: uiState.zoomLevel});

      var currentLayerProvider = layerStyleProviders[applicationModel.getSelectedLayer()]();
      if(currentLayerProvider.defaultStyleProvider)
        return currentLayerProvider.defaultStyleProvider.getStyle(feature, {zoomLevel: uiState.zoomLevel});

      if(currentLayerProvider.default)
        return currentLayerProvider.default.getStyle(feature, {zoomLevel: uiState.zoomLevel});

      return currentLayerProvider.getStyle(feature, {zoomLevel: uiState.zoomLevel});
    }

    function stylesUndefined() {
      return _.isUndefined(layerStyleProviders[applicationModel.getSelectedLayer()]);
    }

    //TODO have a look how we remove styles
    var disableColorsOnRoadLayer = function() {
      if (stylesUndefined()) {
        //vectorLayer.styleMap.styles.default.rules = [];
      }
    };

    //TODO have this on the default style rules
    var changeRoadsWidthByZoomLevel = function() {
      //var featureStyle = styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom());
      //var opacityIndex = featureStyle[0].stroke_.color_.lastIndexOf(", ");
      //featureStyle[0].stroke_.color_ = featureStyle[0].stroke_.color_.substring(0,opacityIndex) + ", 1)";
      //return featureStyle;
      //
      // if (stylesUndefined()) {
      //   var widthBase = 2 + (map.getView().getZoom() - minimumContentZoomLevel());
      //   var roadWidth = widthBase * widthBase;
      //   if (applicationModel.isRoadTypeShown()) {
      //     vectorLayer.setStyle({stroke: roadWidth});
      //   } else {
      //     vectorLayer.setStyle({stroke: roadWidth});
      //     vectorLayer.styleMap.styles.default.defaultStyle.strokeWidth = 5;
      //     vectorLayer.styleMap.styles.select.defaultStyle.strokeWidth = 7;
      //   }
      // }
    };

    var usingLayerSpecificStyleProvider = function(action) {
      if (!_.isUndefined(layerStyleProviders[applicationModel.getSelectedLayer()])) {
        // vectorLayer.style = layerStyleProviders[applicationModel.getSelectedLayer()]();
      }
      action();
    };

    //TODO have a look on the vectorlayer redraw if exists
    var toggleRoadType = function() {
      if (applicationModel.isRoadTypeShown()) {
        //enableColorsOnRoadLayer();
      } else {
        disableColorsOnRoadLayer();
      }
      changeRoadsWidthByZoomLevel();
      usingLayerSpecificStyleProvider(function() {
        //TODO now the redraw will always happen after a source change
        //vectorLayer.redraw();
      });
    };

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
      if (mapState.zoom >= minimumContentZoomLevel()) {
        changeRoadsWidthByZoomLevel();
      } else {
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
    vectorLayer.set('name', 'road');
    map.addLayer(vectorLayer);

    var selectRoadLink = function(roadLink) {
      var feature = _.find(vectorLayer.getSource().getFeatures(), function(feature) {
          if (roadLink.linkId) return feature.getProperties().linkId === roadLink.linkId;
          else return feature.getProperties().roadLinkId === roadLink.roadLinkId;
      });
     // selectControl.unselectAll();
      var featureClone = feature.clone();
      featureClone.setId(feature.getId());

      addSelectionFeatures(featureClone);
    };

    var clearSelection = function(){
      selectControl.getFeatures().clear();
    };

    var addSelectionFeatures = function(feature){
        clearSelection();
     // _.each(features, function(feature){
      selectControl.getFeatures().push(feature);
     // });
    };

    var deactivateSelection = function() {
      enabled = false;
    };

    var activateSelection = function() {
      enabled = true;
    };

    eventbus.on('asset:saved asset:updateCancelled asset:updateFailed', function() {
      //TODO change this to use the new way to do selectcontrol

      //selectControl.unselectAll();
    }, this);

    eventbus.on('road-type:selected', toggleRoadType, this);

    eventbus.on('map:moved', mapMovedHandler, this);

    eventbus.on('layer:selected', function(layer) {
      //TODO have sure this works, i belive it will work because now the style is a funciton and we can get the style from the provider
      //activateLayerStyleMap(layer);
      toggleRoadType();
    }, this);

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
      layer: vectorLayer
    };
  };
})(this);
