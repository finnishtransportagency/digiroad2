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

    var selectRoadLink = function(feature) {
      selectedLinkProperty.open(feature.attributes.mmlId, feature.singleLinkSelect);
      currentRenderIntent = 'select';
      roadLayer.redraw();
      highlightFeatures(feature);
    };

    var unselectRoadLink = function() {
      currentRenderIntent = 'default';
      selectedLinkProperty.close();
      roadLayer.redraw();
      highlightFeatures(null);
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
        if (selectedLinkProperty.isSelected(x.attributes.mmlId)) {
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
      redrawSelected();
      eventbus.trigger('linkProperties:available');
    };

    this.refreshView = function() {
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

    var getSelectedFeatures = function() {
      return _.filter(roadLayer.layer.features, function (feature) {
        return selectedLinkProperty.isSelected(feature.attributes.mmlId);
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
      eventListener.listenTo(eventbus, 'linkProperties:selected', function(link) {
        var feature = _.find(roadLayer.layer.features, function(feature) {
          return feature.attributes.mmlId === link.mmlId;
        });
        if (feature) {
          selectControl.select(feature);
        }
      });
      eventListener.listenTo(eventbus, 'roadLinks:fetched', draw);
      eventListener.listenTo(eventbus, 'linkProperties:dataset:changed', draw);
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
      me.drawOneWaySigns(roadLayer.layer, selectedRoadLinks, geometryUtils);
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
