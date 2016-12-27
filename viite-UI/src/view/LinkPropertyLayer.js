(function(root) {
  root.LinkPropertyLayer = function(map, roadLayer, selectedLinkProperty, roadCollection, linkPropertiesModel, applicationModel) {
    var layerName = 'linkProperty';
    var cachedLinkPropertyMarker = null;
    var cachedMarker = null;
    Layer.call(this, layerName, roadLayer);
    var me = this;
    var eventListener = _.extend({running: false}, eventbus);
    var zoom = 0;
    var currentRenderIntent = 'default';
    var linkPropertyLayerStyles = LinkPropertyLayerStyles(roadLayer);
    this.minZoomForContent = zoomlevels.minZoomForRoadLinks;
    var floatingMarkerLayer = new OpenLayers.Layer.Boxes(layerName);
    map.addLayer(floatingMarkerLayer);
    floatingMarkerLayer.setVisibility(true);

    roadLayer.setLayerSpecificStyleMapProvider(layerName, function() {
      return linkPropertyLayerStyles.getDatasetSpecificStyleMap(linkPropertiesModel.getDataset(), currentRenderIntent);
    });

    var selectRoadLink = function(feature) {
      if(typeof feature.attributes.linkId !== 'undefined') {
        selectedLinkProperty.open(feature.attributes.linkId, feature.attributes.id, feature.singleLinkSelect);
        unhighlightFeatures();
        currentRenderIntent = 'select';
        roadLayer.redraw();
        highlightFeatures();
      }
    };

    var unselectRoadLink = function() {
      currentRenderIntent = 'default';
      selectedLinkProperty.close();
      roadLayer.redraw();
      unhighlightFeatures();
    };

    var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
      onSelect: selectRoadLink,
      onUnselect: unselectRoadLink
    });

    map.addControl(selectControl);
    var doubleClickSelectControl = new DoubleClickSelectControl(selectControl, map);
    this.selectControl = selectControl;

    this.activateSelection = function() {
      doubleClickSelectControl.activate();
    };
    this.deactivateSelection = function() {
      doubleClickSelectControl.deactivate();
    };

    var highlightFeatures = function() {
      _.each(roadLayer.layer.features, function(x) {
        var canIHighlight = !_.isUndefined(x.attributes.linkId) ? selectedLinkProperty.isSelectedByLinkId(x.attributes.linkId) : selectedLinkProperty.isSelectedById(x.attributes.id);
        if (canIHighlight) {
          selectControl.highlight(x);
        } else {
          selectControl.unhighlight(x);
        }
      });
    };

    var unhighlightFeatures = function() {
      _.each(roadLayer.layer.features, function(x) {
        selectControl.unhighlight(x);
      });
    };

    var draw = function() {
      cachedLinkPropertyMarker = new LinkPropertyMarker(selectedLinkProperty);
      prepareRoadLinkDraw();
      var roadLinks = roadCollection.getAll();

      roadLayer.drawRoadLinks(roadLinks, zoom);
      drawDashedLineFeaturesIfApplicable(roadLinks);
      me.drawSigns(roadLayer.layer, roadLinks);

      floatingMarkerLayer.clearMarkers();

      if(zoom > zoomlevels.minZoomForAssets) {
        var floatingRoadMarkers = _.filter(roadLinks, function(roadlink) {
          return roadlink.roadLinkType === -1;
        });
        _.each(floatingRoadMarkers, function(floatlink) {
          var mouseClickHandler = createMouseClickHandler(floatlink);
          var marker = cachedLinkPropertyMarker.createMarker(floatlink);
          marker.events.register('click',marker, mouseClickHandler);
          marker.events.registerPriority('dblclick',marker, mouseClickHandler);
          floatingMarkerLayer.addMarker(marker);
        });
      }

      me.drawRoadNumberMarkers(roadLayer.layer, roadLinks);
      if (zoom > zoomlevels.minZoomForAssets) {
        me.drawCalibrationMarkers(roadLayer.layer, roadLinks);
      }
      redrawSelected();
      eventbus.trigger('linkProperties:available');
    };

    var createMouseClickHandler = function(floatlink) {
      return function(event){
        selectControl.unselectAll();
        var feature = _.find(roadLayer.layer.features, function (feat) {
          return feat.attributes.linkId === floatlink.linkId;
        });
        if(event.type === 'click'){
          selectControl.select(_.assign({singleLinkSelect: false}, feature));
        } else if (event.type === 'dblclick'){
          selectControl.select(_.assign({singleLinkSelect: true}, feature));
        } else {
          selectControl.unselectAll();
        }
      };
    };

    this.refreshView = function() {
      // Generalize the zoom levels as the resolutions and zoom levels differ between map tile sources
      zoom = 11 - Math.round(Math.log(map.getResolution()) * Math.LOG2E);
      roadCollection.fetch(map.getExtent(), zoom);
    };

    this.isDirty = function() {
      return selectedLinkProperty.isDirty();
    };

    var createDashedLineFeatures = function(roadLinks, dashedLineFeature) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var attributes = {
          dashedLineFeature: roadLink[dashedLineFeature],
          linkId: roadLink.linkId,
          type: 'overlay',
          linkType: roadLink.linkType,
          zIndex: 1
        };
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
      }));
    };

    var unknownFeatureSizeLookup = {
      9: { strokeWidth: 3, pointRadius: 0 },
      10: { strokeWidth: 5, pointRadius: 10 },
      11: { strokeWidth: 7, pointRadius: 14 },
      12: { strokeWidth: 10, pointRadius: 16 },
      13: { strokeWidth: 10, pointRadius: 16 },
      14: { strokeWidth: 14, pointRadius: 22 },
      15: { strokeWidth: 14, pointRadius: 22 }
    };

    var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults());
    var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });
    browseStyleMap.addUniqueValueRules('default', 'level', unknownFeatureSizeLookup, applicationModel.zoom);

    var typeFilter = function(type) {
      return new OpenLayers.Filter.Comparison({ type: OpenLayers.Filter.Comparison.EQUAL_TO, property: 'type', value: type });
    };

    var unknownLimitStyleRule = new OpenLayers.Rule({
      filter: typeFilter('roadAddressAnomaly'),
      symbolizer: { externalGraphic: 'images/speed-limits/unknown.svg' }
    });
    browseStyle.addRules([unknownLimitStyleRule]);
    var vectorLayer = new OpenLayers.Layer.Vector(layerName, { styleMap: browseStyleMap });
    vectorLayer.setOpacity(1);
    vectorLayer.setVisibility(true);

    var drawDashedLineFeatures = function(roadLinks) {
      var dashedRoadClasses = [7, 8, 9, 10];
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return _.contains(dashedRoadClasses, roadLink.roadClass);
      });
      roadLayer.layer.addFeatures(createDashedLineFeatures(dashedRoadLinks, 'functionalClass'));
    };

    var drawUnknownUnderConstructionFeatures = function(roadLinks) {
      var constructionTypeValues = [1];
      var type = 'unknownConstructionType';
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return _.contains(constructionTypeValues, roadLink.constructionType) && roadLink.anomaly === 1;
      });
      roadLayer.layer.addFeatures(createDarkDashedLineFeatures(dashedRoadLinks, type));
    };

    var drawDashedLineFeaturesForType = function(roadLinks) {
      var dashedLinkTypes = [2, 4, 6, 8, 12, 21];
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return _.contains(dashedLinkTypes, roadLink.linkType);
      });
      roadLayer.layer.addFeatures(createDashedLineFeatures(dashedRoadLinks, 'linkType'));
    };
    var drawBorderLineFeatures = function(roadLinks) {
      var adminClass = 'Municipality';
      var roadClasses = [1,2,3,4,5,6,7,8,9,10,11];
      var borderLineFeatures = _.filter(roadLinks, function(roadLink) {
        return _.contains(adminClass, roadLink.administrativeClass) && _.contains(roadClasses, roadLink.roadClass) && roadLink.roadLinkType !== -1 && !(roadLink.roadLinkType === -1 && roadLink.roadClasses === 3);
      });
      var features = createBorderLineFeatures(borderLineFeatures, 'functionalClass');
      roadLayer.layer.addFeatures(features);
    };
    var createDarkDashedLineFeatures = function(roadLinks, type){
      return darkDashedLineFeatures(roadLinks, type).concat(calculateMidPointForMarker(roadLinks, type));
    };
    var darkDashedLineFeatures = function(roadLinks, darkDashedLineFeature) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var attributes = {
          dashedLineFeature: roadLink[darkDashedLineFeature],
          linkId: roadLink.linkId,
          type: 'overlay-dark',
          linkType: roadLink.linkType,
          zIndex: 1
        };
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
      }));
    };
    var calculateMidPointForMarker = function(roadLinks, type){
      return _.map(roadLinks, function(link) {
        var points = _.map(link.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var road = new OpenLayers.Geometry.LineString(points);
        var signPosition = GeometryUtils.calculateMidpointOfLineString(road);
        var attributes = {type: type, linkId: link.linkId};
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(signPosition.x, signPosition.y), attributes);
      });
    };
    var createBorderLineFeatures = function(roadLinks) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var attributes = {
          linkId: roadLink.linkId,
          type: 'underlay',
          linkType: roadLink.roadLinkType
        };
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
      }));
    };

    var getSelectedFeatures = function() {
      return _.filter(roadLayer.layer.features, function (feature) {
        return selectedLinkProperty.isSelectedByLinkId(feature.attributes.linkId);
      });
    };

    var reselectRoadLink = function() {
      me.activateSelection();
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      var features = getSelectedFeatures();
      if (!_.isEmpty(features)) {
        currentRenderIntent = 'select';
        selectControl.select(_.first(features));
        highlightFeatures();
      }
      selectControl.onSelect = originalOnSelectHandler;
      if (selectedLinkProperty.isDirty()) {
        me.deactivateSelection();
      }
    };

    var prepareRoadLinkDraw = function() {
      me.deactivateSelection();
    };

    var drawDashedLineFeaturesIfApplicable = function(roadLinks) {
      if (linkPropertiesModel.getDataset() === 'functional-class') {
        drawDashedLineFeatures(roadLinks);
        drawBorderLineFeatures(roadLinks);
        drawUnknownUnderConstructionFeatures(roadLinks);
      } else if (linkPropertiesModel.getDataset() === 'link-type') {
        drawDashedLineFeaturesForType(roadLinks);
      }
    };

    this.layerStarted = function(eventListener) {
      var linkPropertyChangeHandler = _.partial(handleLinkPropertyChanged, eventListener);
      var linkPropertyEditConclusion = _.partial(concludeLinkPropertyEdit, eventListener);
      eventListener.listenTo(eventbus, 'linkProperties:changed', linkPropertyChangeHandler);
      eventListener.listenTo(eventbus, 'linkProperties:cancelled linkProperties:saved', linkPropertyEditConclusion);
      eventListener.listenTo(eventbus, 'linkProperties:saved', refreshViewAfterSaving);
      eventListener.listenTo(eventbus, 'linkProperties:selected linkProperties:multiSelected', function(link) {
        var feature = _.find(roadLayer.layer.features, function(feature) {
          return link.linkId !== 0 && feature.attributes.linkId === link.linkId;
        });
        if (feature) {
          _.each(selectControl.layer.selectedFeatures, function (selectedFeature){
            if(selectedFeature.attributes.linkId !== feature.attributes.linkId) {
              selectControl.select(feature);
            }
          });
        }
      });
      eventListener.listenTo(eventbus, 'linkProperties:reselect', reselectRoadLink);
      eventListener.listenTo(eventbus, 'roadLinks:fetched', draw);
      eventListener.listenTo(eventbus, 'linkProperties:dataset:changed', draw);
      eventListener.listenTo(eventbus, 'linkProperties:updateFailed', cancelSelection);
      eventListener.listenTo(eventbus, 'map:clicked', handleMapClick);
    };

    var handleMapClick = function (){
      selectedLinkProperty.cancel();
      selectedLinkProperty.close();
    };
    
    var cancelSelection = function() {
      selectedLinkProperty.cancel();
      selectedLinkProperty.close();
      unselectRoadLink();
    };

    var refreshViewAfterSaving = function() {
      unselectRoadLink();
      me.refreshView();
    };

    var handleLinkPropertyChanged = function(eventListener) {
      redrawSelected();
      me.deactivateSelection();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
    };

    var concludeLinkPropertyEdit = function(eventListener) {
      me.activateSelection();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      redrawSelected();
    };

    var redrawSelected = function() {
      roadLayer.layer.removeFeatures(getSelectedFeatures());
      var selectedRoadLinks = selectedLinkProperty.get();
      _.each(selectedRoadLinks,  function(selectedLink) { roadLayer.drawRoadLink(selectedLink); });
      drawDashedLineFeaturesIfApplicable(selectedRoadLinks);
      me.drawSigns(roadLayer.layer, selectedRoadLinks);
      reselectRoadLink();
    };

    this.removeLayerFeatures = function() {
      roadLayer.layer.removeFeatures(roadLayer.layer.getFeaturesByAttribute('type', 'overlay'));
    };

    var show = function(map) {
      vectorLayer.setVisibility(true);
      me.show(map);
      eventListener.listenTo(eventbus, 'map:clicked', cancelSelection);
    };

    var hideLayer = function() {
      unselectRoadLink();
      me.stop();
      me.hide();
    };

    return {
      show: show,
      hide: hideLayer,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);
