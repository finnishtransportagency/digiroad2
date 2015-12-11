(function(root) {
  root.PointAssetLayer = function(params) {
    var roadLayer = params.roadLayer,
      collection = params.collection,
      map = params.map,
      roadCollection = params.roadCollection,
      style = params.style,
      selectedAsset = params.selectedAsset,
      mapOverlay = params.mapOverlay,
      layerName = params.layerName,
      newAsset = params.newAsset;

    Layer.call(this, layerName, roadLayer);
    var me = this;
    me.minZoomForContent = zoomlevels.minZoomForAssets;
    var vectorLayer = new OpenLayers.Layer.Vector(layerName, { styleMap: style.browsing });

    me.selectControl = defineOpenLayersSelectControl();
    function defineOpenLayersSelectControl() {
      var selectControl = new OpenLayers.Control.SelectFeature(vectorLayer, {
        onSelect: pointAssetOnSelect,
        onUnselect: pointAssetOnUnselect
      });
      map.addControl(selectControl);

      function pointAssetOnSelect(feature) {
        if (!selectedAsset.isSelected(feature.attributes)) {
          selectedAsset.open(feature.attributes);
        }
      }

      function pointAssetOnUnselect() {
        if (selectedAsset.exists()) {
          selectedAsset.close();
        }
      }

      return selectControl;
    }

    var dragControl = defineOpenLayersDragControl();
    function defineOpenLayersDragControl() {
      var dragControl = new OpenLayers.Control.DragFeature(vectorLayer, { onDrag: handleDragging });
      allowClickEventBubbling();
      map.addControl(dragControl);

      function allowClickEventBubbling() {
        dragControl.handlers.feature.stopClick = false;
      }

      function handleDragging(feature, mousePosition) {
        if (selectedAsset.isSelected(feature.attributes)) {
          var currentLonLat = map.getLonLatFromPixel(new OpenLayers.Pixel(mousePosition.x, mousePosition.y));
          var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), currentLonLat.lon, currentLonLat.lat);
          var newPosition = geometrycalculator.nearestPointOnLine(nearestLine, { x: currentLonLat.lon, y: currentLonLat.lat});
          feature.move(new OpenLayers.LonLat(newPosition.x, newPosition.y));

          selectedAsset.set({lon: feature.geometry.x, lat: feature.geometry.y, mmlId: nearestLine.mmlId});
        } else {
          this.cancel();
        }
      }

      return dragControl;
    }

    function createFeature(asset) {
      return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(asset.lon, asset.lat), asset);
    }

    this.refreshView = function() {
      redrawLinks(map);
      collection.fetch(map.getExtent()).then(function(assets) {
        if (selectedAsset.exists()) {
          var assetsWithoutSelectedAsset = _.reject(assets, {id: selectedAsset.getId()});
          assets = assetsWithoutSelectedAsset.concat([selectedAsset.get()]);
        }

        if (me.isStarted()) {
          withDeactivatedSelectControl(function() {
            me.removeLayerFeatures();
          });
          var features = _.map(assets, createFeature);
          vectorLayer.addFeatures(features);
          applySelection();
        }
      });
    };

    this.removeLayerFeatures = function() {
      vectorLayer.removeAllFeatures();
    };

    function applySelection() {
      if (selectedAsset.exists()) {
        var feature = _.find(vectorLayer.features, function(feature) { return selectedAsset.isSelected(feature.attributes); });
        if (feature) {
          me.selectControl.select(feature);
        }
      }
    }

    function withDeactivatedSelectControl(f) {
      var isActive = me.selectControl.active;
      if (isActive) {
        me.selectControl.deactivate();
        f();
        me.selectControl.activate();
      } else {
        f();
      }
    }

    this.layerStarted = function(eventListener) {
      bindEvents(eventListener);
    };

    function toggleMode(readOnly) {
      if(readOnly){
        dragControl.deactivate();
      } else {
        dragControl.activate();
      }
    }

    function bindEvents(eventListener) {
      eventListener.listenTo(eventbus, 'map:clicked', handleMapClick);
      eventListener.listenTo(eventbus, layerName + ':saved ' + layerName + ':cancelled', handleSavedOrCancelled);
      eventListener.listenTo(eventbus, layerName + ':creationCancelled', handleCreationCancelled);
      eventListener.listenTo(eventbus, layerName + ':selected', handleSelected);
      eventListener.listenTo(eventbus, layerName + ':unselected', handleUnSelected);
      eventListener.listenTo(eventbus, layerName + ':changed', handleChanged);
      eventListener.listenTo(eventbus, 'application:readOnly', toggleMode);
    }

    function handleCreationCancelled() {
      me.selectControl.unselectAll();
      mapOverlay.hide();
      vectorLayer.styleMap = style.browsing;
      me.refreshView();
    }

    function handleSelected() {
      vectorLayer.styleMap = style.selection;
      applySelection();
      vectorLayer.redraw();
    }

    function handleSavedOrCancelled() {
      mapOverlay.hide();
      me.activateSelection();
      me.refreshView();
    }

    function handleChanged() {
      me.deactivateSelection();
      _.find(vectorLayer.features, {attributes: {id: selectedAsset.getId()}}).attributes = selectedAsset.get();
      vectorLayer.redraw();
    }

    function handleMapClick(coordinates) {
      if (applicationModel.getSelectedTool() === 'Add' && zoomlevels.isInAssetZoomLevel(map.getZoom())) {
        var pixel = new OpenLayers.Pixel(coordinates.x, coordinates.y);
        createNewAsset(map.getLonLatFromPixel(pixel));
      } else if (selectedAsset.isDirty()) {
        me.displayConfirmMessage();
      }
    }

    function handleUnSelected() {
      me.selectControl.unselectAll();
      vectorLayer.styleMap = style.browsing;
      vectorLayer.redraw();
    }

    function createNewAsset(coordinates) {
      var selectedLon = coordinates.lon;
      var selectedLat = coordinates.lat;
      var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), selectedLon, selectedLat);
      var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, { x: selectedLon, y: selectedLat });

      var asset = _.merge({}, newAsset, {
        lon: projectionOnNearestLine.x,
        lat: projectionOnNearestLine.y,
        floating: false,
        mmlId: nearestLine.mmlId,
        id: 0
      });

      vectorLayer.addFeatures(createFeature(asset));
      selectedAsset.place(asset);

      mapOverlay.show();
    }

    function redrawLinks(map) {
      eventbus.once('roadLinks:fetched', function () {
        roadLayer.drawRoadLinks(roadCollection.getAll(), map.getZoom());
      });
      roadCollection.fetch(map.getExtent());
    }

    function show(map) {
      map.addLayer(vectorLayer);
      me.show(map);
    }

    function hide() {
      selectedAsset.close();
      map.removeLayer(vectorLayer);
      me.stop();
      me.hide();
    }

    return {
      show: show,
      hide: hide,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);
