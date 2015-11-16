(function(root) {
  root.PointAssetLayer = function(params) {
    var roadLayer = params.roadLayer,
      collection = params.collection,
      map = params.map,
      roadCollection = params.roadCollection,
      selectedAsset = params.selectedAsset;

    Layer.call(this, 'pedestrianCrossing', roadLayer);
    var me = this;
    me.minZoomForContent = zoomlevels.minZoomForAssets;
    var assetLayer = new OpenLayers.Layer.Boxes('pedestrianCrossing');
    var vectorLayer = new OpenLayers.Layer.Vector('pedestrian');

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
    }

    function pointAssetOnUnselect() {
      console.log('Feature unselected');
    }

    function mouseMoveHandler(marker, event) {
      var pixel = new OpenLayers.Pixel(event.xy.x, event.xy.y);
      var lonlat = map.getLonLatFromPixel(pixel);

      var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), lonlat.lon, lonlat.lat);
      var position = geometrycalculator.nearestPointOnLine(nearestLine, { x: lonlat.lon, y: lonlat.lat});

      marker.bounds = OpenLayers.Bounds.fromArray([position.x, position.y, position.x + 15, position.y + 15]);
      assetLayer.redraw();

      selectedAsset.place({ id: selectedAsset.getId(), lon: position.x, lat: position.y, mmlId: nearestLine.mmlId });
    }

    function mouseUpHandler(mouseMoveHandler, event) {
      map.events.unregister('mousemove', map, mouseMoveHandler);
      map.events.unregister('mouseup', assetLayer, mouseUpHandler);
    }

    function mouseDownHandler(event) {
      OpenLayers.Event.stop(event);

      var mouseMoveFn =  _.partial(mouseMoveHandler, event.object);
      map.events.register('mousemove', map, mouseMoveFn);
      map.events.register('mouseup', assetLayer, _.partial(mouseUpHandler, mouseMoveFn));
    }

    function clickHandler(e) {
      var feature = e.object;
      selectedAsset.open(feature.asset);
      feature.events.unregister('click', feature, clickHandler);
      feature.events.register('mousedown', assetLayer, mouseDownHandler);
    }

    function createFeature(asset) {
      var graphics = {
        externalGraphic: 'images/point-assets/point_blue.svg',
        graphicWidth: 14,
        graphicHeight: 14,
        graphicXOffset: -7,
        graphicYOffset: -7
      };

      return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(asset.lon, asset.lat), asset, graphics);
    }

    this.removeLayerFeatures = function() {
      assetLayer.clearMarkers();
    };

    this.refreshView = function() {
      redrawLinks(map);
      collection.fetch(map.getExtent()).then(function(assets) {
        me.removeLayerFeatures();
        _.each(assets, function(asset) {
          vectorLayer.addFeatures(createFeature(asset));
        });
        decorateMarkers();
      });
    };

    function decorateMarkers() {
      if (selectedAsset.exists()) {
        highlightSelected();
      } else {
        unhighlightAll();
      }
    }

    function highlightSelected() {
      var partitioned = _.groupBy(assetLayer.markers, function(marker) {
        return isSelectedAsset(marker.asset);
      });
      var selected = partitioned[true];
      var unSelected = partitioned[false];
      setOpacityForMarkers(selected, '1.0');
      setOpacityForMarkers(unSelected, '0.3');
    }

    function unhighlightAll() {
      setOpacityForMarkers(assetLayer.markers, '1.0');
    }

    function setOpacityForMarkers(markers, opacity) {
      _.each(markers, function(marker) {
        $(marker.div).css('opacity', opacity);
      });
    }

    function isSelectedAsset(asset) {
      return selectedAsset.getId() === asset.id;
    }

    this.layerStarted = function(eventListener) {
      bindEvents(eventListener);
    };

    function bindEvents(eventListener) {
      eventListener.listenTo(eventbus, 'map:clicked', handleMapClick);
      eventListener.listenTo(eventbus, 'pedestrianCrossing:saved', me.refreshView);
      eventListener.listenTo(eventbus, 'pedestrianCrossing:selected', decorateMarkers);
      eventListener.listenTo(eventbus, 'pedestrianCrossing:unselected', decorateMarkers);
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
      map.addLayer(assetLayer);
      map.addLayer(vectorLayer);
      me.show(map);
    }

    function hide() {
      map.removeLayer(assetLayer);
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
