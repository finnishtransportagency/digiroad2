(function(root) {
  root.LinkPropertyLayer = function(map, roadLayer, geometryUtils, selectedLinkProperty, roadCollection, linkPropertiesModel) {
    var layerName = 'linkProperty';
    Layer.call(this, layerName, roadLayer);
    var me = this;
    var currentRenderIntent = 'default';
    var linkPropertyLayerStyles = LinkPropertyLayerStyles(roadLayer);
    this.minZoomForContent = zoomlevels.minZoomForRoadLinks;

    roadLayer.setLayerSpecificStyleMapProvider(layerName, function() {
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
        selectedLinkProperty.open(feature.attributes.mmlId);
        currentRenderIntent = 'select';
        roadLayer.redraw();
        highlightFeatures(feature);
      },
      onUnselect: function() {
        unselectRoadLink();
      }
    });
    this.selectControl = selectControl;
    map.addControl(selectControl);

    var highlightFeatures = function(feature) {
      _.each(roadLayer.layer.features, function(x) {
        if (feature && (x.attributes.mmlId === feature.attributes.mmlId)) {
          selectControl.highlight(x);
        } else {
          selectControl.unhighlight(x);
        }
      });
    };

    var draw = function() {
      prepareRoadLinkDraw();
      var roadLinks = roadCollection.getAll();
      roadLayer.drawRoadLinks(roadLinks, map.getZoom());
      drawDashedLineFeaturesIfApplicable(roadLinks);
      me.drawOneWaySigns(roadLayer.layer, roadLinks, geometryUtils);
      reselectRoadLink();
      eventbus.trigger('linkProperties:available');
    };

    this.refreshView = function() {
      eventbus.once('roadLinks:fetched', function() { draw(); });
      roadCollection.fetchFromVVH(map.getExtent());
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
          mmlId: roadLink.mmlId,
          type: 'overlay',
          linkType: roadLink.linkType
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
      var dashedLinkTypes = [2, 4, 6, 8, 12, 21];
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return _.contains(dashedLinkTypes, roadLink.linkType);
      });
      roadLayer.layer.addFeatures(createDashedLineFeatures(dashedRoadLinks, 'linkType'));
    };

    var reselectRoadLink = function() {
      selectControl.activate();
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      var feature = _.find(roadLayer.layer.features, function(feature) { return feature.attributes.mmlId === selectedLinkProperty.getId(); });
      if (feature) {
        currentRenderIntent = 'select';
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

    var drawDashedLineFeaturesIfApplicable = function(roadLinks) {
      if (linkPropertiesModel.getDataset() === 'functional-class') {
        drawDashedLineFeatures(roadLinks);
      } else if (linkPropertiesModel.getDataset() === 'link-type') {
        drawDashedLineFeaturesForType(roadLinks);
      }
    };

    this.layerStarted = function(eventListener) {
      var linkPropertyChangeHandler = _.partial(handleLinkPropertyChanged, eventListener);
      var linkPropertyEditConclusion = _.partial(concludeLinkPropertyEdit, eventListener);
      eventListener.listenTo(eventbus, 'linkProperties:changed', linkPropertyChangeHandler);
      eventListener.listenTo(eventbus, 'linkProperties:cancelled linkProperties:saved', linkPropertyEditConclusion);
      eventListener.listenTo(eventbus, 'linkProperties:selected', function(link) {
        var feature = _.find(roadLayer.layer.features, function(feature) {
          return feature.attributes.mmlId === link.mmlId;
        });
        if (feature) {
          selectControl.select(feature);
        }
      });
      eventListener.listenTo(eventbus, 'linkProperties:dataset:changed', function(dataset) {
        draw();
      });
    };

    var handleLinkPropertyChanged = function(eventListener) {
      redrawSelected();
      selectControl.deactivate();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
    };

    var concludeLinkPropertyEdit = function(eventListener) {
      selectControl.activate();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      redrawSelected();
    };

    var redrawSelected = function() {
      var selectedFeatures = _.filter(roadLayer.layer.features, function(feature) {
        return feature.attributes.mmlId === selectedLinkProperty.getId();
      });
      roadLayer.layer.removeFeatures(selectedFeatures);
      var data = selectedLinkProperty.get().getData();
      roadLayer.drawRoadLink(data);
      drawDashedLineFeaturesIfApplicable([data]);
      me.drawOneWaySigns(roadLayer.layer, [data], geometryUtils);
      reselectRoadLink();
    };

    this.removeLayerFeatures = function() {
      roadLayer.layer.removeFeatures(roadLayer.layer.getFeaturesByAttribute('type', 'overlay'));
    };

    var show = function(map) {
      if (zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
        me.start();
      }
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
