(function(root) {
  root.LinkPropertyLayer = function(map, roadLayer, geometryUtils, selectedLinkProperty, roadCollection, linkPropertiesModel) {

    var currentRenderIntent = 'default';
    var linkPropertyLayerStyles = LinkPropertyLayerStyles(roadLayer);

    roadLayer.setLayerSpecificStyleMapProvider('linkProperties', function() {
      return linkPropertyLayerStyles.getDatasetSpecificStyleMap(linkPropertiesModel.getDataset(), currentRenderIntent);
    });

    var unselectRoadLink = function() {
      currentRenderIntent = 'default';
      selectedLinkProperty.close();
      roadLayer.redraw();
      highlightFeatures(null);
    };

    var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
      onSelect: function(feature) {
        selectedLinkProperty.open(feature.attributes.roadLinkId);
        currentRenderIntent = 'select';
        roadLayer.redraw();
        highlightFeatures(feature);
      },
      onUnselect: function() {
        unselectRoadLink();
      }
    });

    map.addControl(selectControl);

    var eventListener = _.extend({running: false}, eventbus);
    var highlightFeatures = function(feature) {
      _.each(roadLayer.layer.features, function(x) {
        if (feature && (x.attributes.roadLinkId === feature.attributes.roadLinkId)) {
          selectControl.highlight(x);
        } else {
          selectControl.unhighlight(x);
        }
      });
    };

    var handleMapMoved = function(state) {
      if (zoomlevels.isInRoadLinkZoomLevel(state.zoom) && state.selectedLayer === 'linkProperties') {
        start();
        eventbus.once('roadLinks:fetched', function() {
          prepareRoadLinkDraw();
          var roadLinks = roadCollection.getAll();
          roadLayer.drawRoadLinks(roadLinks, map.getZoom());
          drawDashedLineFeaturesIfApplicable(roadLinks);
          drawOneWaySigns(roadLinks);
          reselectRoadLink();
        });
        roadCollection.fetch(map.getExtent(), map.getZoom());

      } else if (selectedLinkProperty.isDirty()) {
        displayConfirmMessage();
      } else {
        stop();
      }
    };

    var drawOneWaySigns = function(roadLinks) {
      var oneWaySigns = _.chain(roadLinks)
        .filter(function(link) {
          return link.trafficDirection === 'AgainstDigitizing' || link.trafficDirection === 'TowardsDigitizing';
        })
        .map(function(link) {
          var points = _.map(link.points, function(point) {
            return new OpenLayers.Geometry.Point(point.x, point.y);
          });
          var lineString = new OpenLayers.Geometry.LineString(points);
          var signPosition = geometryUtils.calculateMidpointOfLineString(lineString);
          var rotation = link.trafficDirection === 'AgainstDigitizing' ? signPosition.angleFromNorth + 180.0 : signPosition.angleFromNorth;
          var attributes = _.merge({}, link, { rotation: rotation });
          return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(signPosition.x, signPosition.y), attributes);
        })
        .value();

      roadLayer.layer.addFeatures(oneWaySigns);
    };

    var removeOneWaySigns = function() {
      var oneWaySigns = _.filter(roadLayer.layer.features, function(feature) {
        return feature.attributes.rotation !== undefined;
      });
      roadLayer.layer.removeFeatures(oneWaySigns);
    };

    var redrawOneWaySigns = function(roadLinks) {
      removeOneWaySigns();
      drawOneWaySigns(roadLinks);
    };

    var createDashedLineFeatures = function(roadLinks, dashedLineFeature) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var attributes = {
          dashedLineFeature: roadLink[dashedLineFeature],
          roadLinkId: roadLink.roadLinkId,
          type: 'overlay'
        };
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
      }));
    };

    var drawDashedLineFeatures = function(roadLinks) {
      var dashedFunctionalClasses = [2, 4, 6, 8];
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return _.contains(dashedFunctionalClasses, roadLink.functionalClass);
      });
      roadLayer.layer.addFeatures(createDashedLineFeatures(dashedRoadLinks, 'functionalClass'));
    };

    var drawDashedLineFeaturesForType = function(roadLinks) {
      var dashedLinkTypes = [2, 4, 5, 8, 12, 13];
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return _.contains(dashedLinkTypes, roadLink.linkType);
      });
      roadLayer.layer.addFeatures(createDashedLineFeatures(dashedRoadLinks, 'linkType'));
    };

    var reselectRoadLink = function() {
      selectControl.activate();
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      var feature = _.find(roadLayer.layer.features, function(feature) { return feature.attributes.roadLinkId === selectedLinkProperty.getId(); });
      if (feature) {
        selectControl.select(feature);
        highlightFeatures(feature);
      }
      selectControl.onSelect = originalOnSelectHandler;
      if (selectedLinkProperty.get() && selectedLinkProperty.isDirty()) {
        selectControl.deactivate();
      }
    };

    var prepareRoadLinkDraw = function() {
      selectControl.deactivate();
    };

    eventbus.on('map:moved', handleMapMoved);

    var drawDashedLineFeaturesIfApplicable = function(roadLinks) {
      if (linkPropertiesModel.getDataset() === 'functional-class') {
        drawDashedLineFeatures(roadLinks);
      } else if (linkPropertiesModel.getDataset() === 'link-type') {
        drawDashedLineFeaturesForType(roadLinks);
      }
    };

    var start = function() {
      if (!eventListener.running) {
        eventListener.running = true;
        eventbus.once('roadLinks:fetched', function() {
          prepareRoadLinkDraw();
          var roadLinks = roadCollection.getAll();
          roadLayer.drawRoadLinks(roadLinks, map.getZoom());
          drawDashedLineFeaturesIfApplicable(roadLinks);
          drawOneWaySigns(roadLinks);
          reselectRoadLink();
        });
        roadCollection.fetch(map.getExtent(), map.getZoom());

        eventListener.listenTo(eventbus, 'linkProperties:changed', handleLinkPropertyChanged);
        eventListener.listenTo(eventbus, 'linkProperties:cancelled linkProperties:saved', concludeLinkPropertyEdit);
        eventListener.listenTo(eventbus, 'linkProperties:selected', function(link) {
          var feature = _.find(roadLayer.layer.features, function(feature) {
            return feature.attributes.roadLinkId === link.roadLinkId;
          });
          selectControl.select(feature);
        });
        eventListener.listenTo(eventbus, 'linkProperties:dataset:changed', function(dataset) {
          prepareRoadLinkDraw();
          roadLayer.drawRoadLinks(roadCollection.getAll(), map.getZoom());
          drawDashedLineFeaturesIfApplicable(roadCollection.getAll());
          drawOneWaySigns(roadCollection.getAll());
          reselectRoadLink();
        });
        selectControl.activate();
        drawDashedLineFeaturesIfApplicable(roadCollection.getAll());
        redrawOneWaySigns(roadCollection.getAll());
      }
    };

    var displayConfirmMessage = function() { new Confirm(); };

    var handleLinkPropertyChanged = function() {
      redrawSelected();
      selectControl.deactivate();
      eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
      eventListener.listenTo(eventbus, 'map:clicked', displayConfirmMessage);
    };

    var concludeLinkPropertyEdit = function() {
      selectControl.activate();
      eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
      redrawSelected();
    };

    var redrawSelected = function() {
      var selectedFeatures = _.filter(roadLayer.layer.features, function(feature) {
        return feature.attributes.roadLinkId === selectedLinkProperty.getId();
      });
      roadLayer.layer.removeFeatures(selectedFeatures);
      var data = selectedLinkProperty.get().getData();
      roadLayer.drawRoadLink(data);
      drawDashedLineFeaturesIfApplicable([data]);
      drawOneWaySigns([data]);
      reselectRoadLink();
    };

    var stop = function() {
      roadLayer.layer.removeFeatures(roadLayer.layer.getFeaturesByAttribute('type', 'overlay'));
      selectControl.deactivate();
      eventListener.stopListening(eventbus);
      eventListener.running = false;
    };

    var show = function(map) {
      if (zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
        start();
      }
    };

    var hide = function() {
      unselectRoadLink();
      stop();
    };

    return {
      show: show,
      hide: hide,
      minZoomForContent: zoomlevels.minZoomForRoadLinks
    };
  };
})(this);
