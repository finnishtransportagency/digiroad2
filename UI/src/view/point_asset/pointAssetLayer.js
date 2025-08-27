(function(root) {
  root.PointAssetLayer = function(params) {
    var roadLayer = params.roadLayer,
      application= applicationModel,
      collection = params.collection,
      map = params.map,
      roadCollection = params.roadCollection,
      style = params.style,
      selectedAsset = params.selectedAsset,
      mapOverlay = params.mapOverlay,
      layerName = params.layerName,
      typeId = params.typeId,
      newAsset = params.newAsset,
      roadAddressInfoPopup = params.roadAddressInfoPopup,
      assetLabel = params.assetLabel,
      allowGrouping = params.allowGrouping,
      assetGrouping = params.assetGrouping,
      authorizationPolicy = params.authorizationPolicy,
      trafficSignReadOnlyLayer = params.readOnlyLayer;
    var pointAssetLayerStyles = PointAssetLayerStyles(params.roadLayer);

    Layer.call(this, layerName, roadLayer);
    var me = this;
    me.minZoomForContent = zoomlevels.minZoomForAssets;
    var extraEventListener = _.extend({running: false}, eventbus);
    var vectorSource = new ol.source.Vector();
    me.vectorLayer = new ol.layer.Vector({
       source : vectorSource,
       style : function(feature){
         if (layerName == 'trafficLights') {
           return style.browsingStyleProvider.getStyle(feature, {'selectedId': selectedAsset.getId()});
         } else {
           return style.browsingStyleProvider.getStyle(feature);
         }
       },
      renderBuffer: 300
    });
    me.vectorLayer.set('name', layerName);
    me.vectorLayer.setOpacity(1);
    me.vectorLayer.setVisible(true);
    map.addLayer(me.vectorLayer);

    var editingRestrictions = new EditingRestrictions();
    var servicePointTypeId = 250;

    var selectControl = new SelectToolControl(application, me.vectorLayer, map, false,{
        style : function (feature) {
          if (layerName == 'trafficLights') {
            return style.browsingStyleProvider.getStyle(feature, {'selectedId': selectedAsset.getId()});
          } else {
            return style.browsingStyleProvider.getStyle(feature);
          }
        },
        onSelect : pointAssetOnSelect,
        draggable : false,
        filterGeometry : function(feature){
           return feature.getGeometry() instanceof ol.geom.Point;
        }
    });

    function pointAssetOnSelect(feature) {
      if(feature.selected.length > 0 && feature.deselected.length === 0){
          var properties = feature.selected[0].getProperties();
          var administrativeClass = obtainAdministrativeClass(properties);
          var municipalityCode = obtainMunicipalityCode(properties);
          var asset = _.merge({}, properties, {administrativeClass: administrativeClass});
          selectedAsset.open(asset);
          if (authorizationPolicy.formEditModeAccess(selectedAsset, roadCollection) && !applicationModel.isReadOnly()) {
            if (typeId === servicePointTypeId && !(editingRestrictions.pointAssetHasRestriction(selectedAsset.get().municipalityCode, 'State', typeId) || editingRestrictions.pointAssetHasRestriction(selectedAsset.get().municipalityCode, 'Municipality', typeId))) {
              dragControl.activate();
            } else if (typeId !== servicePointTypeId && !editingRestrictions.pointAssetHasRestriction(municipalityCode, administrativeClass, typeId) &&
                !(authorizationPolicy.isElyMaintainer() && administrativeClass === 'Municipality')) {
              dragControl.activate();
            }
          }
      }
      else {
        if(feature.deselected.length > 0 && !selectedAsset.isDirty()) {
          selectedAsset.close();
          if(trafficSignReadOnlyLayer){
            trafficSignReadOnlyLayer.highLightLayer();
          }
        }else{
          applySelection();
        }
        dragControl.deactivate();
      }
    }

    this.selectControl = selectControl;

    var dragControl = defineOpenLayersDragControl();
    function defineOpenLayersDragControl() {
        var dragHandler = layerName === 'servicePoints' ? dragFreely : dragAlongNearestLink;
        var dragControl = new ol.interaction.Translate({
           features : selectControl.getSelectInteraction().getFeatures()
        });

        dragControl.on('translating', dragHandler);

        function dragFreely(feature) {
          if (selectedAsset.isSelected(feature.features.getArray()[0].getProperties())) {
            selectedAsset.set({lon: feature.coordinate[0], lat: feature.coordinate[1]});
          }
        }

        function dragAlongNearestLink(feature) {
          if (selectedAsset.isSelected(feature.features.getArray()[0].getProperties())) {
            var nearestLine = geometrycalculator.findNearestLine(me.excludeRoads(roadCollection, feature), feature.coordinate[0], feature.coordinate[1]);
            if (nearestLine) {
              var newPosition = geometrycalculator.nearestPointOnLine(nearestLine, { x: feature.coordinate[0], y: feature.coordinate[1]});
              roadLayer.selectRoadLink(roadCollection.getRoadLinkByLinkId(nearestLine.linkId).getData());
              feature.features.getArray()[0].getGeometry().setCoordinates([newPosition.x, newPosition.y]);
              var newBearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);

              var asset = selectedAsset.get();
              if(layerName == "trafficLights" && !isOld(asset)){
                var bearingProps = _.filter(asset.propertyData, {'publicId': 'bearing'});
                _.forEach(bearingProps, function (prop) {
                  selectedAsset.setPropertyByGroupedIdAndPublicId(prop.groupedId, prop.publicId, newBearing);
                });
              }

              selectedAsset.set({lon: newPosition.x, lat: newPosition.y, mValue: null, linkId: nearestLine.linkId, geometry: feature.features.getArray()[0].getGeometry(), floating: false, bearing: newBearing});
            }
          }
        }

        var activate = function () {
            map.addInteraction(dragControl);
        };

        var deactivate = function () {
          var forRemove = _.filter(map.getInteractions().getArray(), function(interaction) {
              return interaction instanceof ol.interaction.Translate;
          });
          _.each(forRemove, function (interaction) {
             map.removeInteraction(interaction);
          });
        };

        return {
            activate : activate,
            deactivate : deactivate
        };
    }

    this.createFeature = function(asset) {
      var rotation = determineRotation(asset);
      var bearing = determineBearing(asset);
      var administrativeClass = obtainAdministrativeClass(asset);
      var constructionType = obtainConstructionType(asset);
      var feature =  new ol.Feature({geometry : new ol.geom.Point([asset.lon, asset.lat])});
      var obj = _.merge({}, asset, {rotation: rotation, bearing: bearing, administrativeClass: administrativeClass,
        constructionType: constructionType}, feature.getProperties());
      feature.setProperties(obj);
      return feature;
    };

    function isOld(asset){
      var typeProp = _.find(asset.propertyData, {'publicId': 'trafficLight_type'});
      return _.head(typeProp.values).propertyValue === "";
    }

    function determineRotation(asset) {
      var rotation = 0;
      if (layerName == 'trafficLights'){
        if(asset.id === 0 && selectedAsset.getId() !== 0){
          rotation = validitydirections.calculateRotation(determineBearing(asset), asset.validityDirection);
        }else if(!isOld(asset)){
          var bearingProps = _.filter(asset.propertyData, {'publicId': 'bearing'});
          if (asset.id == selectedAsset.getId() && selectedAsset.getSelectedGroupedId()) {
            _.forEach(bearingProps, function (prop) {
              if (_.isEmpty(prop.values) || _.head(prop.values).propertyValue === "")
                selectedAsset.setPropertyByGroupedIdAndPublicId(prop.groupedId, prop.publicId, determineBearing(asset));
            });

            var bearingProp = selectedAsset.getPropertyByGroupedIdAndPublicId(selectedAsset.getSelectedGroupedId(), 'bearing');
            var bearingValue = _.head(bearingProp.values).propertyValue;

            var sideCodeProp = selectedAsset.getPropertyByGroupedIdAndPublicId(selectedAsset.getSelectedGroupedId(), 'sidecode');
            var sideCodeValue = _.head(sideCodeProp.values).propertyValue;

            rotation = validitydirections.calculateRotation(parseFloat(bearingValue), parseInt(sideCodeValue));
          } else {
            var isBearingNotDetermined = _.isEmpty(_.head(bearingProps).values) || _.head(_.head(bearingProps).values).propertyValue === "" || _.head(_.head(bearingProps).values).propertyValue;
            var firstBearingValue = isBearingNotDetermined ? determineBearing(asset) : _.head(_.head(bearingProps).values).propertyValue;
            var firstSideCodeValue = _.head(_.find(asset.propertyData, {'publicId': 'sidecode'}).values).propertyValue;
            rotation = validitydirections.calculateRotation(parseFloat(firstBearingValue), parseInt(firstSideCodeValue));
          }
        }
      } else if (!asset.floating && asset.geometry && asset.geometry.length > 0){
        var bearing = determineBearing(asset);
        rotation = validitydirections.calculateRotation(bearing, asset.validityDirection);
      } else if (layerName == 'directionalTrafficSigns' || !_.isUndefined(asset.bearing) && layerName == 'trafficSigns'){
        rotation = validitydirections.calculateRotation(asset.bearing, asset.validityDirection);
      }
      return rotation;
    }

    function determineBearing(asset) {
      var bearing = 90;
      var nearestLine;

      if (!asset.floating && asset.geometry && asset.geometry.length > 0){
        nearestLine = geometrycalculator.findNearestLine([{ points: asset.geometry }], asset.lon, asset.lat);
        bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
      } else if (layerName == 'directionalTrafficSigns' || layerName == 'trafficSigns'){
        bearing = asset.bearing;
      } else if (layerName == 'trafficLights' && !isOld(asset)){
        var bearingPropValues = _.find(asset.propertyData, {'publicId': 'bearing'}).values;
        if(_.isEmpty(bearingPropValues) || _.head(bearingPropValues).propertyValue === ""){
          nearestLine = geometrycalculator.findNearestLine(me.excludeRoadByAdminClass(roadCollection.getRoadsForCarPedestrianCycling()), asset.lon, asset.lat);
          bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
        }else{
          bearing = _.head(bearingPropValues).propertyValue;
        }
      }
      return bearing;
    }

    this.refreshView = function() {
      var laneInformationToolTip=false;
      if (layerName =="trafficSigns"){
        laneInformationToolTip=true;
      }
      eventbus.once('roadLinks:fetched', function () {
        var roadLinks = roadCollection.getAll();
        roadLayer.drawRoadLinks(roadLinks, zoomlevels.getViewZoom(map));
        me.drawOneWaySigns(roadLayer.layer, roadLinks);
        selectControl.activate();
      });
      if(collection.complementaryIsActive()) {
        roadCollection.fetchWithComplementary(map.getView().calculateExtent(map.getSize()),laneInformationToolTip);
        if (trafficSignReadOnlyLayer)
          trafficSignReadOnlyLayer.refreshView();
      }
      else
      roadCollection.fetch(map.getView().calculateExtent(map.getSize()),laneInformationToolTip);
      collection.fetch(map.getView().calculateExtent(map.getSize()), map.getView().getCenter(),laneInformationToolTip).then(function(assets) {
        if (selectedAsset.exists()) {
          var assetsWithoutSelectedAsset = _.reject(assets, {id: selectedAsset.getId()});
          assets = assetsWithoutSelectedAsset.concat([selectedAsset.get()]);
        }

        if (me.isStarted()) {
          withDeactivatedSelectControl(function() {
            me.removeLayerFeatures();
          });
          var features = (!allowGrouping) ? _.map(assets, me.createFeature) : getGroupedFeatures(assets);
          selectControl.clear();
          me.vectorLayer.getSource().addFeatures(features);
          var assetsWithConstructionType = _.map(assets, function(asset) {
            asset.constructionType = obtainConstructionType(asset);
            return asset;
          });
          if(assetLabel)
            me.vectorLayer.getSource().addFeatures(assetLabel.renderFeaturesByPointAssets(assetsWithConstructionType, zoomlevels.getViewZoom(map)));
          applySelection();
        }

        if (trafficSignReadOnlyLayer)
          trafficSignReadOnlyLayer.refreshView();
      });
    };

    this.stop = function() {
      if (me.isStarted()) {
        me.removeLayerFeatures();
        me.deactivateSelection();
        me.eventListener.stopListening(eventbus);
        me.eventListener.running = false;
        handleUnSelected();
      }
    };

    var getGroupedFeatures = function (assets) {
      var assetGroups = assetGrouping.groupByDistance(assets, zoomlevels.getViewZoom(map));
      var modifiedAssets = _.forEach(assetGroups, function (assetGroup) {
        _.map(assetGroup, function (asset) {
          asset.lon = _.head(assetGroup).lon;
          asset.lat = _.head(assetGroup).lat;
        });
      });
      return _.map(_.flatten(modifiedAssets), me.createFeature);
    };

    function obtainAdministrativeClass(asset){
      return selectedAsset.getAdministrativeClass(asset.linkId);
    }

    function obtainConstructionType(asset) {
      return selectedAsset.getConstructionType(asset.linkId);
    }

    function obtainMunicipalityCode(asset) {
      return selectedAsset.getMunicipalityCodeByLinkId(asset.linkId);
    }

    this.removeLayerFeatures = function() {
      me.vectorLayer.getSource().clear();
    };

    function applySelection() {
      if (selectedAsset.exists()) {
        var feature = _.filter(me.vectorLayer.getSource().getFeatures(), function(feature) { return selectedAsset.isSelected(feature.getProperties());});
        if (feature) {
          selectControl.addSelectionFeatures(feature);
        }
      }
    }

    function withDeactivatedSelectControl(f) {
      var isActive = me.selectControl.active;
      if (isActive) {
          selectControl.deactivate();
        f();
          selectControl.activate();
      } else {
        f();
      }
    }

    this.layerStarted = function(eventListener) {
      this.bindEvents(eventListener);
      showRoadLinkInformation();
    };

    function toggleMode(readOnly) {
      if(readOnly){
        dragControl.deactivate();
      } else if(selectedAsset.exists() && authorizationPolicy.formEditModeAccess(selectedAsset, roadCollection)) {
        if (typeId === servicePointTypeId && !(editingRestrictions.pointAssetHasRestriction(selectedAsset.getMunicipalityCode(), 'State', typeId) ||
            editingRestrictions.pointAssetHasRestriction(selectedAsset.getMunicipalityCode(), 'Municipality', typeId))) {
          dragControl.activate();
        } else if (typeId !== servicePointTypeId && !editingRestrictions.pointAssetHasRestriction(selectedAsset.getMunicipalityCode(), selectedAsset.getAdministrativeClass(), typeId) &&
            !(authorizationPolicy.isElyMaintainer() && selectedAsset.getAdministrativeClass() === 'Municipality')) {
          dragControl.activate();
        }
      }
    }

    this.bindEvents = function(eventListener) {
      eventListener.listenTo(eventbus, 'map:clicked', this.handleMapClick);
      eventListener.listenTo(eventbus, layerName + ':saved ' + layerName + ':cancelled', handleSavedOrCancelled);
      eventListener.listenTo(eventbus, layerName + ':creationCancelled', handleCreationCancelled);
      eventListener.listenTo(eventbus, layerName + ':selected', handleSelected);
      eventListener.listenTo(eventbus, layerName + ':unselected', handleUnSelected);
      eventListener.listenTo(eventbus, layerName + ':changed', handleChanged);
      eventListener.listenTo(eventbus, 'application:readOnly', toggleMode);
      eventListener.listenTo(eventbus, 'toggleWithRoadAddress', refreshSelectedView);
    };

    eventbus.on( layerName + ':changeSigns', function(trafficSignData){
      setTrafficSigns(trafficSignData[0], trafficSignData[1]);
    });

    eventbus.on( layerName + ':signsChanged', function(trafficSignsShowing) {
      selectedAsset.checkSelectedSign(trafficSignsShowing);
    });

    var setTrafficSigns = function(trafficSign, isShowing) {
      collection.setTrafficSigns(trafficSign, isShowing);
      me.refreshView();
    };

    function startListeningExtraEvents(){
      extraEventListener.listenTo(eventbus, layerName+'-complementaryLinks:show', showWithComplementary);
      extraEventListener.listenTo(eventbus, layerName+'-complementaryLinks:hide', hideComplementary);
    }

    function stopListeningExtraEvents(){
      extraEventListener.stopListening(eventbus);
    }

    function handleCreationCancelled() {
      mapOverlay.hide();
      unHighLightReadOnlyLayer();
      roadLayer.clearSelection();
      me.refreshView();
    }

    function handleSelected() {
      applySelection();
    }

    function handleUnSelected() {
       selectControl.clear();
    }

    function handleSavedOrCancelled() {
      mapOverlay.hide();
      me.activateSelection();
      roadLayer.clearSelection();
      me.refreshView();
    }

    function handleChanged() {
      var asset = selectedAsset.get();
      var featureRedraw = _.filter(me.vectorLayer.getSource().getFeatures(), function (feature) {
        return feature.getProperties().id === asset.id;
      });
      var newProperties = {
        'rotation': determineRotation(asset),
        'bearing': determineBearing(asset),
        'administrativeClass': obtainAdministrativeClass(asset),
        'constructionType': obtainConstructionType(asset),
        'geometry': new ol.geom.Point([asset.lon, asset.lat])
      };
      _.forEach(featureRedraw, function (feature) {
        feature.setProperties(newProperties);
        if (layerName === 'trafficLights') {
          feature.setProperties({'propertyData': asset.propertyData});
        }
      });
      selectControl.addSelectionFeatures(featureRedraw);
    }

    this.createNewAsset =  function(coordinates) {
      var selectedLon = coordinates.x;
      var selectedLat = coordinates.y;
      var nearestLine = geometrycalculator.findNearestLine(me.excludeRoadByAdminClass(roadCollection.getRoadsForPointAssets()), selectedLon, selectedLat);
      if(nearestLine.end && nearestLine.start){
        var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, { x: selectedLon, y: selectedLat });
        var bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
        var administrativeClass = obtainAdministrativeClass(nearestLine);
        var constructionType = obtainConstructionType(nearestLine);
        var municipalityCode = obtainMunicipalityCode(nearestLine);

        if (administrativeClass === 'State' && editingRestrictions.pointAssetHasRestriction(municipalityCode, administrativeClass, typeId)) {
          me.displayAssetCreationRestricted('Kohteiden lisääminen on estetty, koska kohteita ylläpidetään Tievelho-tietojärjestelmässä.');
          return;
        }

        if (administrativeClass === 'Municipality' && editingRestrictions.pointAssetHasRestriction(municipalityCode, administrativeClass, typeId)) {
          me.displayAssetCreationRestricted('Kunnan kohteiden lisääminen on estetty, koska kohteita ylläpidetään kunnan omassa tietojärjestelmässä.');
          return;
        }

        if (administrativeClass === 'Municipality' && authorizationPolicy.isElyMaintainer()) {
          me.displayAssetCreationRestricted('Käyttöoikeudet eivät riitä kohteen lisäämiseen. ELY-ylläpitäjänä et voi lisätä kohteita kunnan omistamalle katuverkolle.');
          return;
        }

        var asset = me.createAssetWithPosition(selectedLat, selectedLon, nearestLine, projectionOnNearestLine, bearing,
            administrativeClass, constructionType);

        me.vectorLayer.getSource().addFeature(me.createFeature(asset));
        selectedAsset.place(asset);
        mapOverlay.show();
      }
    };

    this.handleMapClick = function (coordinates) {
      if (application.getSelectedTool() === 'Add' && zoomlevels.isInAssetZoomLevel(zoomlevels.getViewZoom(map))) {
        me.createNewAsset(coordinates);
      } else if (selectedAsset.isDirty()) {
        me.displayConfirmMessage();
      }
    };

    this.createAssetWithPosition = function(selectedLat, selectedLon, nearestLine, projectionOnNearestLine, bearing, administrativeClass) {
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
        bearing: bearing,
        administrativeClass: administrativeClass
      });
    };

    function showWithComplementary() {
      if(trafficSignReadOnlyLayer)
        trafficSignReadOnlyLayer.showTrafficSignsComplementary();
      collection.activeComplementary(true);
      me.refreshView();
    }

    this.showLayer = function(map) {
      startListeningExtraEvents();
      me.vectorLayer.setVisible(true);
      roadAddressInfoPopup.start();
      me.show(map);
    };

    function hideComplementary() {
      if(trafficSignReadOnlyLayer)
        trafficSignReadOnlyLayer.hideTrafficSignsComplementary();
      collection.activeComplementary(false);
      selectedAsset.close();
      me.refreshView();
    }

    this.hideLayer = function() {
      selectedAsset.close();
      me.vectorLayer.setVisible(false);
      hideReadOnlyLayer();
      roadAddressInfoPopup.stop();
      stopListeningExtraEvents();
      me.stop();
      me.hide();
    };

    var hideReadOnlyLayer = function(){
      if(trafficSignReadOnlyLayer){
        trafficSignReadOnlyLayer.hide();
        trafficSignReadOnlyLayer.removeLayerFeatures();
      }
    };

    var unHighLightReadOnlyLayer = function(){
      if(trafficSignReadOnlyLayer){
        trafficSignReadOnlyLayer.unHighLightLayer();
      }
    };

    this.excludeRoads = function(roadCollection) {
      return me.excludeRoadByAdminClass(roadCollection.getRoadsForPointAssets());
    };

    this.excludeRoadByAdminClass = function(roads) {
      return _.filter(roads, function (road) {
        return authorizationPolicy.filterRoadLinks(road);
      });
    };

    var refreshSelectedView = function(){
      if(applicationModel.getSelectedLayer() == layerName)
        me.refreshView();
    };

    function showRoadLinkInformation() {
      if(params.showRoadLinkInfo) {
        roadLayer.setLayerSpecificStyleProvider(params.layerName, function() {
          return pointAssetLayerStyles;
        });
      }
    }

    return {
      show: me.showLayer,
      hide: me.hideLayer,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);
