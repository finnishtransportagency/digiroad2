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
   // var vectorLayer = new OpenLayers.Layer.Vector(layerName, { styleMap: style.browsing });

    var vectorSource = new ol.source.Vector();
    var vectorLayer = new ol.layer.Vector({
       source : vectorSource,
       style : function(feature){
           return style.browsingStyleProvider.getStyle(feature);
       }
    });
    vectorLayer.setOpacity(1);
    vectorLayer.setVisible(true);
    map.addLayer(vectorLayer);

    me.selectControl = defineOpenLayersSelectControl();
    function defineOpenLayersSelectControl() {

      var selectControl = new ol.interaction.Select({
         layers : [vectorLayer],
         condition : function(events){ return enable && ol.events.condition.singleClick(events); },
         style : function (feature) {
             return feature.setStyle(style.browsingStyleProvider.getStyle(feature));
         }
      });
      selectControl.on('select', pointAssetOnSelect);

      function pointAssetOnSelect(feature) {
        if(feature.selected.length > 0){
          selectedAsset.open(feature.selected[0].values_);
          toggleMode(applicationModel.isReadOnly());
        }
        else {
          if(feature.deselected.length > 0) {
            selectedAsset.close();
          }
        }
      }
      var enable = false;
      console.log('init');
      map.addInteraction(selectControl);

      var activate = function () {
         enable=true;
         console.log('activate');
      };

      var deactivate = function () {
        enable = false;
        console.log('deactivate');
        //map.removeInteraction(selectControl);
      };

      return {
          selectControl : selectControl,
          activate : activate,
          deactivate : deactivate
      };
    }

    var dragControl = defineOpenLayersDragControl();
    function defineOpenLayersDragControl() {
        var dragHandler = layerName === 'servicePoints' ? dragFreely : dragAlongNearestLink;
        var dragControl = new ol.interaction.Translate({
           features : me.selectControl.selectControl.getFeatures()
        });

        dragControl.on('translating', dragHandler);

        function dragFreely(feature) {
          if (selectedAsset.isSelected(feature.attributes)) {
            selectedAsset.set({lon: feature.coordinate[0], lat: feature.coordinate[1]});
          }
        }

        //TODO : remove feature.features.array_[0] to feature.getFeatures()
        function dragAlongNearestLink(feature) {
          if (selectedAsset.isSelected(feature.features.array_[0].getProperties())) {
           // var currentLonLat = map.getLonLatFromPixel(new OpenLayers.Pixel(mousePosition.x, mousePosition.y));
            var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), feature.coordinate[0], feature.coordinate[1]);
            if (nearestLine) {
              var newPosition = geometrycalculator.nearestPointOnLine(nearestLine, { x: feature.coordinate[0], y: feature.coordinate[1]});
              roadLayer.selectRoadLink(roadCollection.getRoadLinkByLinkId(nearestLine.linkId).getData());
              feature.features.array_[0].getGeometry().setCoordinates([newPosition.x, newPosition.y]);
              var newBearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
              selectedAsset.set({lon: newPosition.x, lat: newPosition.y, linkId: nearestLine.linkId, geometry: feature.features.array_[0].getGeometry(), floating: false, bearing: newBearing});
            }
          }
        }

        var activate = function () {
            map.addInteraction(dragControl);
        };

        var deactivate = function () {
            map.removeInteraction(dragControl);
        };

        return {
            activate : activate,
            deactivate : deactivate
        };
    }

    function createFeature(asset) {
      var rotation = determineRotation(asset);
      var bearing = determineBearing(asset);
      var feature =  new ol.Feature({geometry : new ol.geom.Point([asset.lon, asset.lat])});
      var obj = _.merge({}, asset, {rotation: rotation, bearing: bearing}, feature.values_);
      feature.setProperties(obj);
      return feature;
    }

    //TODO: edit validity-direction
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
        roadLayer.drawRoadLinks(roadCollection.getAll(), map.getView().getZoom());
          me.selectControl.activate();
      });
      roadCollection.fetch(map.getView().calculateExtent(map.getSize()));
      collection.fetch(map.getView().calculateExtent(map.getSize())).then(function(assets) {
        if (selectedAsset.exists()) {
          var assetsWithoutSelectedAsset = _.reject(assets, {id: selectedAsset.getId()});
          assets = assetsWithoutSelectedAsset.concat([selectedAsset.get()]);
        }

        if (me.isStarted()) {
          withDeactivatedSelectControl(function() {
            me.removeLayerFeatures();
          });
          var features = _.map(assets, createFeature);
          vectorLayer.getSource().addFeatures(features);
          applySelection();
        }
      });
    };

    this.removeLayerFeatures = function() {
      vectorLayer.getSource().clear();
    };

    function applySelection() {
      if (selectedAsset.exists()) {
        var feature = _.find(vectorLayer.getSource().getFeatures(), function(feature) { return selectedAsset.isSelected(feature.values_); });
        if (feature) {
          //me.selectControl.select(feature);
            me.selectControl.activate();
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
    //  me.selectControl.unselectAll();
    //  me.activateSelection();
      mapOverlay.hide();
        roadLayer.clearSelection();
    //  vectorLayer.styleMap = style.browsing;
      me.refreshView();
    }

    function handleSelected() {
    //  vectorLayer.styleMap = style.selection;
      applySelection();
      //vectorLayer.redraw();
    }

    function handleSavedOrCancelled() {
        me.selectControl.activate();
      mapOverlay.hide();
      //TODO use that instead of doing me.selectControl.activate();
     // me.activateSelection();
        roadLayer.clearSelection();
      me.refreshView();
    }

    function handleChanged() {
      me.deactivateSelection();
      var asset = selectedAsset.get();
      var newAsset = _.merge({}, asset, {rotation: determineRotation(asset), bearing: determineBearing(asset)});
      // _.find(vectorLayer.features, {attributes: {id: newAsset.id}}).attributes = newAsset;
      _.find(vectorLayer.getSource().getFeatures(), {values_: {id: newAsset.id}}).values_= newAsset;
      //vectorLayer.redraw();

    }

    function handleMapClick(coordinates) {
      if (applicationModel.getSelectedTool() === 'Add' && zoomlevels.isInAssetZoomLevel(map.getView().getZoom())) {
       // var pixel = new OpenLayers.Pixel(coordinates.x, coordinates.y);
        createNewAsset(coordinates);
      } else if (selectedAsset.isDirty()) {
        me.displayConfirmMessage();
      }
    }

    function handleUnSelected() {
        me.selectControl.activate();
      //me.selectControl.unselectAll();
      //vectorLayer.styleMap = style.browsing;
      //vectorLayer.redraw();
    }

    function createNewAsset(coordinates) {
      var selectedLon = coordinates.x;
      var selectedLat = coordinates.y;
      var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), selectedLon, selectedLat);
      var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, { x: selectedLon, y: selectedLat });
      var bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);

      var asset = createAssetWithPosition(selectedLat, selectedLon, nearestLine, projectionOnNearestLine, bearing);

      vectorLayer.getSource().addFeature(createFeature(asset));
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
        linkId: nearestLine.linkId,
        id: 0,
        geometry: [nearestLine.start, nearestLine.end],
        bearing: bearing
      });
    }

    function show(map) {
      vectorLayer.setVisible(true);
      //map.addLayer(vectorLayer);
      me.show(map);
    }

    function hide() {
      //selectedAsset.close();
      vectorLayer.setVisible(false);
      //map.removeLayer(vectorLayer);
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
