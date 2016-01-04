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
      var dragHandler = layerName === 'servicePoints' ? dragFreely : dragAlongNearestLink;
      var dragControl = new OpenLayers.Control.DragFeature(vectorLayer, { onDrag: dragHandler });
      allowClickEventBubbling();
      map.addControl(dragControl);

      function allowClickEventBubbling() {
        dragControl.handlers.feature.stopClick = false;
      }

      function dragFreely(feature, mousePosition) {
        if (selectedAsset.isSelected(feature.attributes)) {
          var currentLonLat = map.getLonLatFromPixel(new OpenLayers.Pixel(mousePosition.x, mousePosition.y));
          selectedAsset.set({lon: currentLonLat.lon, lat: currentLonLat.lat});
        } else {
          this.cancel();
        }
      }

      function dragAlongNearestLink(feature, mousePosition) {
        if (selectedAsset.isSelected(feature.attributes)) {
          var currentLonLat = map.getLonLatFromPixel(new OpenLayers.Pixel(mousePosition.x, mousePosition.y));
          var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), currentLonLat.lon, currentLonLat.lat);
          var newPosition = geometrycalculator.nearestPointOnLine(nearestLine, { x: currentLonLat.lon, y: currentLonLat.lat});
          roadLayer.selectRoadLink(nearestLine);
          feature.move(new OpenLayers.LonLat(newPosition.x, newPosition.y));
          var newBearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
          selectedAsset.set({lon: feature.geometry.x, lat: feature.geometry.y, mmlId: nearestLine.mmlId, geometry: [nearestLine.start, nearestLine.end], floating: false, bearing: newBearing});
        } else {
          this.cancel();
        }
      }

      return dragControl;
    }

    function createFeature(asset) {
      var rotation = determineRotation(asset);
      var bearing = determineBearing(asset);
      return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(asset.lon, asset.lat), _.merge({}, asset, {rotation: rotation, bearing: bearing}));
    }

    function determineRotation(asset) {
      var rotation = 0;
      if (!asset.floating && asset.geometry && asset.geometry.length > 0){
        var bearing = determineBearing(asset);
        rotation = validitydirections.calculateRotation(bearing, asset.validityDirection);
      } else if (layerName == 'directionalTrafficSigns'){
        rotation = validitydirections.calculateRotation(asset.bearing, asset.validityDirection);
      }
      return rotation;
    }

    function determineBearing(asset) {
      var bearing = 90;
      if (!asset.floating && asset.geometry && asset.geometry.length > 0){
        var nearestLine = geometrycalculator.findNearestLine([{ points: asset.geometry }], asset.lon, asset.lat);
        bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
      } else if (layerName == 'directionalTrafficSigns'){
        bearing = asset.bearing;
      }
      return bearing;
    }


    this.refreshView = function() {
      eventbus.once('roadLinks:fetched', function () {
        roadLayer.drawRoadLinks(roadCollection.getAll(), map.getZoom());
      });
      roadCollection.fetch(map.getExtent());
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
      me.activateSelection();
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
      var asset = selectedAsset.get();
      var newAsset = _.merge({}, asset, {rotation: determineRotation(asset), bearing: determineBearing(asset)});
      _.find(vectorLayer.features, {attributes: {id: newAsset.id}}).attributes = newAsset;
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
      var bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);

      var asset = createAssetWithPosition(selectedLat, selectedLon, nearestLine, projectionOnNearestLine, bearing);

      vectorLayer.addFeatures(createFeature(asset));
      selectedAsset.place(asset);

      mapOverlay.show();
    }

    function createAssetWithPosition(selectedLat, selectedLon, nearestLine, projectionOnNearestLine, bearing) {
      var isServicePoint = newAsset.services;

      return _.merge({}, newAsset, isServicePoint ? {
        lon: selectedLon,
        lat: selectedLat,
        id: 0
      } : {
        lon: projectionOnNearestLine.x,
        lat: projectionOnNearestLine.y,
        floating: false,
        mmlId: nearestLine.mmlId,
        id: 0,
        geometry: [nearestLine.start, nearestLine.end],
        bearing: bearing
      });
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
