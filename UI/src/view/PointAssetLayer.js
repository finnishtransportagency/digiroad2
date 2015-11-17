(function(root) {
  root.PointAssetLayer = function(params) {
    var roadLayer = params.roadLayer,
      collection = params.collection,
      map = params.map,
      roadCollection = params.roadCollection,
      style = params.style,
      selectedAsset = params.selectedAsset;

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
      console.log('Selecting feature: ', feature);
      selectedAsset.open(feature.attributes);
      vectorLayer.styleMap = style.selection;
      vectorLayer.redraw();
    }

    function pointAssetOnUnselect() {
      console.log('Feature unselected');
      vectorLayer.styleMap = style.browsing;
      vectorLayer.redraw();
    }

    function mouseMoveHandler(marker, event) {
      var pixel = new OpenLayers.Pixel(event.xy.x, event.xy.y);
      var lonlat = map.getLonLatFromPixel(pixel);

      var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), lonlat.lon, lonlat.lat);
      var position = geometrycalculator.nearestPointOnLine(nearestLine, { x: lonlat.lon, y: lonlat.lat});

      marker.bounds = OpenLayers.Bounds.fromArray([position.x, position.y, position.x + 15, position.y + 15]);

      selectedAsset.place({ id: selectedAsset.getId(), lon: position.x, lat: position.y, mmlId: nearestLine.mmlId });
    }

    function mouseUpHandler(mouseMoveHandler, event) {
      map.events.unregister('mousemove', map, mouseMoveHandler);
      // map.events.unregister('mouseup', assetLayer, mouseUpHandler);
    }

    function mouseDownHandler(event) {
      OpenLayers.Event.stop(event);

      var mouseMoveFn =  _.partial(mouseMoveHandler, event.object);
      map.events.register('mousemove', map, mouseMoveFn);
      // map.events.register('mouseup', assetLayer, _.partial(mouseUpHandler, mouseMoveFn));
    }

    function clickHandler(e) {
      var feature = e.object;
      selectedAsset.open(feature.asset);
      feature.events.unregister('click', feature, clickHandler);
      // feature.events.register('mousedown', assetLayer, mouseDownHandler);
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
        var features = _.map(assets, function(asset) {
          return createFeature(asset);
        });
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

    function highlightSelected() {
      // var partitioned = _.groupBy(assetLayer.markers, function(marker) {
      //   return isSelectedAsset(marker.asset);
      // });
      // var selected = partitioned[true];
      // var unSelected = partitioned[false];
      var selected = [];
      var unselected = [];
      setOpacityForMarkers(selected, '1.0');
      setOpacityForMarkers(unselected, '0.3');
    }

    function unhighlightAll() {
      // TODO: Implement using OpenLayers style maps or set feature opacities explicitly
    }

    function setOpacityForMarkers(markers, opacity) {
      // TODO: Implement using OpenLayers style maps or set feature opacities explicitly
    }

    this.layerStarted = function(eventListener) {
      bindEvents(eventListener);
    };

    function bindEvents(eventListener) {
      eventListener.listenTo(eventbus, 'map:clicked', handleMapClick);
      eventListener.listenTo(eventbus, 'pedestrianCrossing:saved', me.refreshView);
      // eventListener.listenTo(eventbus, 'pedestrianCrossing:selected', decorateFeatures);
      // eventListener.listenTo(eventbus, 'pedestrianCrossing:unselected', decorateFeatures);
    }

    function handleMapClick(coordinates) {
      if (applicationModel.getSelectedTool() === 'Add') {
        var pixel = new OpenLayers.Pixel(coordinates.x, coordinates.y);
        createNewAsset(map.getLonLatFromPixel(pixel));
      } else if (selectedAsset.isDirty()) {
        me.displayConfirmMessage();
      } else {
        selectedAsset.close();
      }
    }

    function createNewAsset(coordinates) {
      var selectedLon = coordinates.lon;
      var selectedLat = coordinates.lat;
      var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), selectedLon, selectedLat);
      var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, { x: selectedLon, y: selectedLat });

      var crossing = {
        lon: projectionOnNearestLine.x,
        lat: projectionOnNearestLine.y,
        mmlId: nearestLine.mmlId
      };

      selectedAsset.place(crossing);
      vectorLayer.addFeatures(createFeature(crossing));
      eventbus.trigger('pedestrianCrossing:opened');
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
      me.selectControl.unselectAll();
      vectorLayer.styleMap = style.browsing;
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
