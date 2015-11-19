(function(root) {
  root.PointAssetLayer = function(params) {
    var roadLayer = params.roadLayer,
      collection = params.collection,
      map = params.map,
      roadCollection = params.roadCollection,
      style = params.style,
      selectedAsset = params.selectedAsset,
      mapOverlay = params.mapOverlay;

    Layer.call(this, 'pedestrianCrossing', roadLayer);
    var me = this;
    me.minZoomForContent = zoomlevels.minZoomForAssets;
    var vectorLayer = new OpenLayers.Layer.Vector('pedestrianCrossing', { styleMap: style.browsing });
    defineOpenLayersSelectControl();
    function defineOpenLayersSelectControl() {
      me.selectControl = new OpenLayers.Control.SelectFeature(vectorLayer, {
        onSelect: pointAssetOnSelect,
        onUnselect: pointAssetOnUnselect
      });
      map.addControl(me.selectControl);
    }

    function pointAssetOnSelect(feature) {
      selectedAsset.open(feature.attributes);
    }

    function pointAssetOnUnselect() {
      selectedAsset.close();
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
        var isSelected = selectedAsset.getId() === feature.attributes.id;
        if (isSelected) {
          var currentLonLat = map.getLonLatFromPixel(new OpenLayers.Pixel(mousePosition.x, mousePosition.y));
          var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), currentLonLat.lon, currentLonLat.lat);
          var newPosition = geometrycalculator.nearestPointOnLine(nearestLine, { x: currentLonLat.lon, y: currentLonLat.lat});
          feature.move(new OpenLayers.LonLat(newPosition.x, newPosition.y));

          feature.attributes.lon = feature.geometry.x;
          feature.attributes.lat = feature.geometry.y;
          feature.attributes.mmlId = nearestLine.mmlId;
          selectedAsset.move(feature.attributes);
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
        withDeactivatedSelectControl(function() {
          me.removeLayerFeatures();
        });
        var features = _.map(assets, createFeature);
        vectorLayer.addFeatures(features);
        applySelection();
      });
    };

    this.removeLayerFeatures = function() {
      vectorLayer.removeAllFeatures();
    };

    function applySelection() {
      if (selectedAsset.exists()) {
        withoutOnSelect(function() {
          var feature = _.find(vectorLayer.features, function(feature) { return selectedAsset.isSelected(feature.attributes); });
          if (feature) {
            me.selectControl.select(feature);
          }
        });
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

    function withoutOnSelect(f) {
      me.selectControl.onSelect = function() {};
      f();
      me.selectControl.onSelect = pointAssetOnSelect;
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
      eventListener.listenTo(eventbus, 'pedestrianCrossing:saved pedestrianCrossing:cancelled', handleSavedOrCancelled);
      eventListener.listenTo(eventbus, 'pedestrianCrossing:selected', handleSelected);
      eventListener.listenTo(eventbus, 'pedestrianCrossing:unselected', handleUnSelected);
      eventListener.listenTo(eventbus, 'pedestrianCrossing:changed', handleChanged);
      eventListener.listenTo(eventbus, 'application:readOnly', toggleMode);
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
    }

    function handleMapClick(coordinates) {
      if (applicationModel.getSelectedTool() === 'Add') {
        var pixel = new OpenLayers.Pixel(coordinates.x, coordinates.y);
        createNewAsset(map.getLonLatFromPixel(pixel));
      } else if (selectedAsset.isDirty()) {
        me.displayConfirmMessage();
      }
    }

    function handleUnSelected() {
      withoutOnSelect(function() {
        me.selectControl.unselectAll();
      });
      vectorLayer.styleMap = style.browsing;
      vectorLayer.redraw();
    }

    function createNewAsset(coordinates) {
      var selectedLon = coordinates.lon;
      var selectedLat = coordinates.lat;
      var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), selectedLon, selectedLat);
      var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, { x: selectedLon, y: selectedLat });

      var crossing = {
        lon: projectionOnNearestLine.x,
        lat: projectionOnNearestLine.y,
        floating: false,
        mmlId: nearestLine.mmlId,
        id: 0
      };

      vectorLayer.addFeatures(createFeature(crossing));
      selectedAsset.place(crossing);

      mapOverlay.show();
    }

    function redrawLinks(map) {
      eventbus.once('roadLinks:fetched', function () {
        roadLayer.drawRoadLinks(roadCollection.getAll(), map.getZoom());
      });
      roadCollection.fetchFromVVH(map.getExtent());
    }

    function show(map) {
      redrawLinks(map);
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
      hide: hide
    };
  };
})(this);
