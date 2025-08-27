(function(root) {
root.LinearAssetLayer  = function(params) {
  var map = params.map,
      application = params.application,
      collection = params.collection,
      selectedLinearAsset = params.selectedLinearAsset,
      roadLayer = params.roadLayer,
      multiElementEventCategory = params.multiElementEventCategory,
      singleElementEventCategory = params.singleElementEventCategory,
      style = params.style,
      layerName = params.layerName,
      typeId = params.typeId,
      assetLabel = params.assetLabel,
      roadAddressInfoPopup = params.roadAddressInfoPopup,
      massLimitation = params.massLimitation === 'Muut massarajoitukset',
      trafficSignReadOnlyLayer = params.readOnlyLayer,
      isMultipleLinkSelectionAllowed = params.isMultipleLinkSelectionAllowed,
      authorizationPolicy = params.authorizationPolicy,
      isExperimental = params.isExperimental,
      minZoomForContent = params.minZoomForContent;

  Layer.call(this, layerName, roadLayer);
  var me = this;
  this.minZoomForContent = isExperimental && minZoomForContent ? minZoomForContent : zoomlevels.minZoomForAssets;
  var isComplementaryChecked = false;
  var extraEventListener = _.extend({running: false}, eventbus);
  var editingRestrictions = new EditingRestrictions();

  this.singleElementEvents = function() {
    return _.map(arguments, function(argument) { return singleElementEventCategory + ':' + argument; }).join(' ');
  };

  this.multiElementEvent = function(eventName) {
    return multiElementEventCategory + ':' + eventName;
  };

  var LinearAssetCutter = function(eventListener, vectorLayer, collection) {
    var scissorFeatures = [];
    var CUT_THRESHOLD = 20;
    var vectorSource = vectorLayer.getSource();

    var moveTo = function(x, y) {
      scissorFeatures = [new ol.Feature({geometry: new ol.geom.Point([x, y]), type: 'cutter' })];
      me.selectToolControl.removeFeatures(function(feature) {
            return feature.getProperties().type === 'cutter';
      });
      me.selectToolControl.addNewFeature(scissorFeatures, true);
    };

    var remove = function () {
      me.selectToolControl.removeFeatures(function(feature) {
          return feature && feature.getProperties().type === 'cutter';
      });
      scissorFeatures = [];
    };

    var self = this;

    var clickHandler = function(evt) {
      if (application.getSelectedTool() === 'Cut' && me.selectableZoomLevel()) {
        if (collection.isDirty()) {
          me.displayConfirmMessage();
        } else {
          self.cut(evt);
        }
      }
    };

    this.deactivate = function() {
      eventListener.stopListening(eventbus, 'map:clicked', clickHandler);
      eventListener.stopListening(eventbus, 'map:mouseMoved');
      remove();
    };

    this.activate = function() {
      eventListener.listenTo(eventbus, 'map:clicked', clickHandler);
      eventListener.listenTo(eventbus, 'map:mouseMoved', function(event) {
        if (application.getSelectedTool() === 'Cut' && !collection.isDirty()) {
          self.updateByPosition(event.coordinate);
        }
      });
    };

    var isWithinCutThreshold = function(linearAssetLink) {
      return linearAssetLink && linearAssetLink < CUT_THRESHOLD;
    };

    var findNearestLinearAssetLink = function(point) {
      return _.chain(vectorSource.getFeatures())
        .filter(function(feature) {
          return feature.getGeometry() instanceof ol.geom.LineString;
        })
        .reject(function(feature) {
          var properties = feature.getProperties();
          return _.has(properties, 'generatedId') && _.flatten(collection.getGroup(properties)).length > 0;
        })
        .map(function(feature) {
          var closestP = feature.getGeometry().getClosestPoint(point);
          //TODO be sure about this distance
          var distanceBetweenPoints = GeometryUtils.distanceOfPoints(point, closestP);
          return {
            feature: feature,
            point: closestP,
            distance: distanceBetweenPoints
          };
        })
        .sortBy(function(nearest) {
          return nearest.distance;
        })
        .head()
        .value();
    };

    this.updateByPosition = function(mousePoint) {
      var closestLinearAssetLink = findNearestLinearAssetLink(mousePoint);
      if (closestLinearAssetLink) {
        var nearestLineAsset = closestLinearAssetLink.feature.getProperties();
        if (authorizationPolicy.formEditModeAccess(nearestLineAsset)) {
          if (isWithinCutThreshold(closestLinearAssetLink.distance)) {
            moveTo(closestLinearAssetLink.point[0], closestLinearAssetLink.point[1]);
          } else {
            remove();
          }
        }
      }
    };

    this.cut = function(mousePoint) {
      var pointsToLineString = function(points) {
        var coordPoints = _.map(points, function(point) { return [point.x, point.y]; });
        return new ol.geom.LineString(coordPoints);
      };

      var calculateSplitProperties = function(nearestLinearAsset, point) {
        var lineString = pointsToLineString(nearestLinearAsset.points);
        var startMeasureOffset = nearestLinearAsset.startMeasure;
        var splitMeasure = GeometryUtils.calculateMeasureAtPoint(lineString, point) + startMeasureOffset;
        var splitVertices = GeometryUtils.splitByPoint(pointsToLineString(nearestLinearAsset.points), point);
        return _.merge({ splitMeasure: splitMeasure }, splitVertices);
      };

      var nearest = findNearestLinearAssetLink([mousePoint.x, mousePoint.y]);

      if (!isWithinCutThreshold(nearest.distance)) {
        return;
      }

      var nearestLinearAsset = nearest.feature.getProperties();
      if(authorizationPolicy.formEditModeAccess(nearestLinearAsset)) {
        var splitProperties = calculateSplitProperties(nearestLinearAsset, mousePoint);
        selectedLinearAsset.splitLinearAsset(nearestLinearAsset.id, splitProperties);

        remove();
      }
    };
  };

  this.uiState = { zoomLevel: 9 };

  this.vectorSource = new ol.source.Vector();
  this.vectorLayer = new ol.layer.Vector({
    source : me.vectorSource,
    style : function(feature) {
      return me.getLayerStyle(feature);
    }
  });

  this.getLayerStyle = function(feature)  {
      return style.browsingStyleProvider.getStyle(feature, {zoomLevel: me.uiState.zoomLevel});
  };

  this.vectorLayer.set('name', layerName);
  this.vectorLayer.setOpacity(1);
  this.vectorLayer.setVisible(false);
  map.addLayer(me.vectorLayer);

  this.indicatorVector = new ol.source.Vector({});
  this.indicatorLayer = new ol.layer.Vector({
     source : me.indicatorVector
  });
  map.addLayer(me.indicatorLayer);
  me.indicatorLayer.setVisible(false);
  this.readOnlyLayer = massLimitation ? new GroupedLinearAssetLayer(params, map) : {
    refreshView: function () {},
    redrawLinearAssets: function (linearAssetChains) {},
    hideLayer: function () {},
    showLayer: function () {},
    removeLayerFeatures: function () {},
    showWithComplementary: function () {},
    hideComplementary: function () {}
  };

  var linearAssetCutter = new LinearAssetCutter(me.eventListener, me.vectorLayer, collection);

  this.selectableZoomLevel = function() {
    return me.uiState.zoomLevel >= zoomlevels.minZoomForAssets;
  };

  this.onSelect = function(evt) {
   if(me.selectableZoomLevel()) {
     if(evt.selected.length !== 0) {
       var feature = evt.selected[0];
       var properties = feature.getProperties();
       verifyClickEvent(properties, evt);
     }else{
       if (selectedLinearAsset.exists()) {
         selectedLinearAsset.close();
         me.readOnlyLayer.showLayer();
         me.highLightReadOnlyLayer();
       }
     }
   }
  };

  var onMultipleSelect = function(evt) {
   if(me.selectableZoomLevel()){
     if(evt.selected.length !== 0) {
       selectedLinearAsset.addSelection(_.map(evt.selected, function(feature){ return feature.getProperties();}));
     }
     else{
       if (selectedLinearAsset.exists()) {
         selectedLinearAsset.removeSelection(_.map(evt.deselected, function(feature){ return feature.getProperties();}));
       }
     }
   }
  };

  var verifyClickEvent = function(properties, evt){
    var singleLinkSelect = evt.mapBrowserEvent.type === 'dblclick';
    selectedLinearAsset.open(properties, singleLinkSelect);
    me.highlightMultipleLinearAssetFeatures();
  };

  this.highlightMultipleLinearAssetFeatures = function() {
    me.readOnlyLayer.hideLayer();
    unHighLightReadOnlyLayer();
  };

  this.selectToolControl = new SelectToolControl(application, me.vectorLayer, map, isMultipleLinkSelectionAllowed, {
    style: function(feature){ return feature.setStyle(me.getLayerStyle(feature)); },
    onInteractionEnd: onInteractionEnd,
    onSelect: me.onSelect,
    onMultipleSelect: onMultipleSelect,
    onClose: onCloseForm,
    enableSelect: me.selectableZoomLevel
  });

  this.getSelectToolControl = function() {
    return me.selectToolControl;
  };

  this.getVectorSource = function() {
    return me.vectorSource;
  };

  var showDialog = function (linearAssets) {
      linearAssets = _.filter(linearAssets, function(asset){
          return asset && !(asset.geometry instanceof ol.geom.Point) && authorizationPolicy.formEditModeAccess(asset);
      });

      if(_.isEmpty(linearAssets))
        return;

      selectedLinearAsset.openMultiple(linearAssets);

      if (editingRestrictions.hasStateRestriction(selectedLinearAsset.get(), typeId)) {
        me.displayAssetCreationRestricted('Kohteiden lisääminen on estetty, koska kohteita ylläpidetään Tievelho-tietojärjestelmässä.');
        selectedLinearAsset.close();
        return;
      } else if(editingRestrictions.hasMunicipalityRestriction(selectedLinearAsset.get(), typeId)) {
        me.displayAssetCreationRestricted('Kunnan kohteiden lisääminen on estetty, koska kohteita ylläpidetään kunnan omassa tietojärjestelmässä.');
        selectedLinearAsset.close();
        return;
      } else if (editingRestrictions.elyUserRestrictionOnMunicipalityAsset(authorizationPolicy, selectedLinearAsset.get())) {
        me.displayAssetCreationRestricted('Käyttöoikeudet eivät riitä kohteen lisäämiseen. ELY-ylläpitäjänä et voi lisätä kohteita kunnan omistamalle katuverkolle.');
        return;
      }

      var features = style.renderFeatures(selectedLinearAsset.get());
      if(assetLabel)
         features = features.concat(assetLabel.renderFeaturesByLinearAssets(_.map(selectedLinearAsset.get(), me.offsetBySideCode), me.uiState.zoomLevel));
      me.selectToolControl.addSelectionFeatures(features);

     LinearAssetMassUpdateDialog.show({
        count: selectedLinearAsset.count(),
        onCancel: me.cancelSelection,
        onSave: function (value) {
          selectedLinearAsset.saveMultiple(value);
          me.selectToolControl.clear();
          selectedLinearAsset.closeMultiple();
        me.selectToolControl.deactivateDraw();},
        validator: selectedLinearAsset.validator,
        formElements: params.formElements,
       selectedLinearAsset: selectedLinearAsset,
       assetTypeConfiguration: params
      });
  };

  function onInteractionEnd(linearAssets) {
    if (selectedLinearAsset.isDirty()) {
        me.displayConfirmMessage();
    } else {
        if (linearAssets.length > 0 && me.selectableZoomLevel()) {
            selectedLinearAsset.close();
            showDialog(linearAssets);
            onCloseForm();
        }
    }
  }

  this.cancelSelection = function() {
    if(isComplementaryChecked)
      showWithComplementary();
    else
      hideComplementary();
    me.selectToolControl.clear();
    selectedLinearAsset.closeMultiple();
  };

  this.adjustStylesByZoomLevel = function(zoom) {
    me.uiState.zoomLevel = zoom;
  };

  var changeTool = function(tool) {
    switch(tool) {
      case 'Cut':
        me.selectToolControl.deactivate();
        linearAssetCutter.activate();
        break;
      case 'Select':
        linearAssetCutter.deactivate();
        me.selectToolControl.deactivateDraw();
        me.selectToolControl.activate();
        break;
      case 'Rectangle':
        linearAssetCutter.deactivate();
        me.selectToolControl.activeRectangle();
        break;
      case 'Polygon':
        linearAssetCutter.deactivate();
        me.selectToolControl.activePolygon();
        break;
      default:
    }
  };

  function onCloseForm()  {
    eventbus.trigger('closeForm');
  }

  this.bindEvents = function(eventListener) {
    var linearAssetChanged = _.partial(handleLinearAssetChanged, eventListener);
    var linearAssetCancelled = _.partial(handleLinearAssetCancelled, eventListener);
    eventListener.listenTo(eventbus, me.singleElementEvents('unselect'), linearAssetUnSelected);
    eventListener.listenTo(eventbus, me.singleElementEvents('selected'), me.linearAssetSelected);
    eventListener.listenTo(eventbus, me.multiElementEvent('fetched'), redrawLinearAssets);
    eventListener.listenTo(eventbus, 'tool:changed', changeTool);
    eventListener.listenTo(eventbus, me.singleElementEvents('saved'), me.handleLinearAssetSaved);
    eventListener.listenTo(eventbus, me.multiElementEvent('massUpdateSucceeded'), me.handleLinearAssetSaved);
    eventListener.listenTo(eventbus, me.singleElementEvents('valueChanged', 'separated'), linearAssetChanged);
    eventListener.listenTo(eventbus, me.singleElementEvents('cancelled', 'saved'), linearAssetCancelled);
    eventListener.listenTo(eventbus, me.multiElementEvent('cancelled'), linearAssetCancelled);
    eventListener.listenTo(eventbus, me.singleElementEvents('selectByLinkId'), selectLinearAssetByLinkId);
    eventListener.listenTo(eventbus, me.multiElementEvent('massUpdateFailed'), me.cancelSelection);
    eventListener.listenTo(eventbus, me.multiElementEvent('valueChanged'), linearAssetChanged);
    eventListener.listenTo(eventbus, 'toggleWithRoadAddress', refreshSelectedView);
    eventListener.listenTo(eventbus, 'layer:linearAsset', me.refreshReadOnlyLayer);
  };

  var startListeningExtraEvents = function(){
    extraEventListener.listenTo(eventbus, layerName + '-complementaryLinks:show', showWithComplementary);
    extraEventListener.listenTo(eventbus, layerName + '-complementaryLinks:hide', hideComplementary);
    extraEventListener.listenTo(eventbus, layerName + '-walkingCyclingLinks:show', showWalkingCycling);
    extraEventListener.listenTo(eventbus, layerName + '-walkingCyclingLinks:hide', hideWalkingCycling);
  };

  this.stopListeningExtraEvents = function(){
    extraEventListener.stopListening(eventbus);
  };

  var selectLinearAssetByLinkId = function(linkId) {
    var feature = _.find(me.vectorLayer.features, function(feature) { return feature.attributes.linkId === linkId; });
    if (feature) {
        me.selectToolControl.addSelectionFeatures([feature]);
    }
  };

  var linearAssetUnSelected = function () {
    me.selectToolControl.clear();
    if (application.getSelectedTool() !== 'Cut')
      changeTool(application.getSelectedTool());
    me.eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
  };
  
  this.linearAssetSelected = function(){
      me.decorateSelection();
  };

  this.handleLinearAssetSaved = function() {
    me.refreshView();
    applicationModel.setSelectedTool('Select');
  };

  var handleLinearAssetChanged = function(eventListener, selectedLinearAsset, polygonSelection) {
    //Disable interaction so the user can not click on another feature after made changes
    me.selectToolControl.deactivate();
    eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
    eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
    me.decorateSelection(polygonSelection);
  };

  this.refreshReadOnlyLayer = function () {
    if(massLimitation)
      me.readOnlyLayer.refreshView();
  };

  this.layerStarted = function(eventListener) {
    me.bindEvents(eventListener);
  };

  this.refreshView = function() {
    me.vectorLayer.setVisible(true);
    me.adjustStylesByZoomLevel(zoomlevels.getViewZoom(map));
    if (isComplementaryChecked) {
      collection.fetchAssetsWithComplementary(map.getView().calculateExtent(map.getSize()), map.getView().getCenter(), Math.round(map.getView().getZoom())).then(function() {
        eventbus.trigger('layer:linearAsset');
      });
    } else {
      collection.fetch(map.getView().calculateExtent(map.getSize()), map.getView().getCenter(), Math.round(map.getView().getZoom())).then(function() {
        eventbus.trigger('layer:linearAsset');
      });
    }
    if(trafficSignReadOnlyLayer){
      trafficSignReadOnlyLayer.refreshView();
    }
  };

  this.activateSelection = function() {
    me.selectToolControl.activate();
  };
  this.deactivateSelection = function() {
    me.selectToolControl.deactivate();
  };
  this.removeLayerFeatures = function() {
    me.vectorLayer.getSource().clear();
    me.indicatorLayer.getSource().clear();
    me.readOnlyLayer.removeLayerFeatures();
  };

  var handleLinearAssetCancelled = function(eventListener) {
    me.selectToolControl.clear();
    if(application.getSelectedTool() !== 'Cut'){
      me.selectToolControl.activate();
    }
    eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
    redrawLinearAssets(collection.getAll());
  };

  this.drawIndicators = function(links) {
    var features = [];

    var markerContainer = function(link, position) {
        var anchor, offset;
        if(assetLabel){
            anchor = assetLabel.getMarkerAnchor(me.uiState.zoomLevel);
            offset = assetLabel.getMarkerOffset(me.uiState.zoomLevel);
        }

        var imageSettings = {src: 'images/center-marker2.svg'};
        if(anchor)
            imageSettings = _.merge(imageSettings, { anchor : anchor });

        var textSettings = {
            text : link.marker,
            fill: new ol.style.Fill({
                color: '#ffffff'
            }),
            font : '12px sans-serif'
        };
        if(offset)
          textSettings = _.merge(textSettings, {offsetX : offset[0], offsetY : offset[1]});

        var style = new ol.style.Style({
            image : new ol.style.Icon(imageSettings),
            text : new ol.style.Text(textSettings)
        });
        var marker = new ol.Feature({
            geometry : new ol.geom.Point([position.x, position.y])
        });
        marker.setStyle(style);
        features.push(marker);
    };

    var indicatorsForSplit = function() {
      return me.mapOverLinkMiddlePoints(links, function(link, middlePoint) {
          markerContainer(link, middlePoint);
      });
    };

    var indicatorsForSeparation = function() {
      var geometriesForIndicators = _.map(links, function(link) {
        var newLink = _.cloneDeep(link);
        newLink.points = _.drop(newLink.points, 1);
        return newLink;
      });

      return me.mapOverLinkMiddlePoints(geometriesForIndicators, function(link, middlePoint) {
          markerContainer(link, middlePoint);
      });
    };

    var indicators = function() {
      if (selectedLinearAsset.isSplit()) {
        return indicatorsForSplit();
      }
      return indicatorsForSeparation();
    };
    indicators();
    me.selectToolControl.addNewFeature(features);
  };

  var redrawLinearAssets = function(linearAssetChains) {
    me.vectorSource.clear();
    me.indicatorLayer.getSource().clear();
    var linearAssets = _.flatten(linearAssetChains);
      me.decorateSelection();
      me.drawLinearAssets(linearAssets, me.vectorSource);
  };

  this.drawLinearAssets = function(linearAssets) {
    var allButSelected = _.filter(linearAssets, function(asset){ return !_.some(selectedLinearAsset.get(), function(selectedAsset){
      return selectedAsset.linkId === asset.linkId && selectedAsset.startMeasure === asset.startMeasure && selectedAsset.endMeasure === asset.endMeasure; }) ;
    });
    me.vectorSource.addFeatures(style.renderFeatures(allButSelected));
    me.readOnlyLayer.showLayer();
    me.highLightReadOnlyLayer();
    if(assetLabel) {
      var splitChangedAssets = _.partition(allButSelected, function(a){ return (a.sideCode !== 1 && _.has(a, 'value'));});
      me.vectorSource.addFeatures(assetLabel.renderFeaturesByLinearAssets(_.map( _.cloneDeep(_.omit(splitChangedAssets[0], 'geometry')), me.offsetBySideCode), me.uiState.zoomLevel));
      me.vectorSource.addFeatures(assetLabel.renderFeaturesByLinearAssets(_.map( _.omit(splitChangedAssets[1], 'geometry'), me.offsetBySideCode), me.uiState.zoomLevel));
    }
  };

  this.offsetBySideCode = function (linearAsset) {
    return GeometryUtils.offsetBySideCode(applicationModel.zoom.level, linearAsset);
  };

  this.removeFeature = function(feature) {
    return me.vectorSource.removeFeature(feature);
  };

  this.geometryAndValuesEqual = function(feature, comparison) {
    var toCompare = ["linkId", "sideCode", "startMeasure", "endMeasure"];
    _.each(toCompare, function(value){
      return feature[value] === comparison[value];
    });
  };

  this.decorateSelection = function (polygonSelection) {
    if (selectedLinearAsset.exists()) {

      var linearAssets = selectedLinearAsset.get();
      var selectedFeatures = style.renderFeatures(linearAssets);

      if(assetLabel){
        if(polygonSelection){
          var selectedLabels = _.filter(me.vectorSource.getFeatures(), function(layerFeature){ return _.some(selectedFeatures, function(selectedFeature){
            return layerFeature.values_.geometry instanceof ol.geom.Point && (me.geometryAndValuesEqual(selectedFeature.values_, layerFeature.values_)); }) ;
          });
          _.each(selectedLabels, me.removeFeature);

          selectedFeatures = selectedFeatures.concat(assetLabel.renderFeaturesByLinearAssets(linearAssets, me.uiState.zoomLevel));
        } else {
          var currentFeatures = _.filter(me.vectorSource.getFeatures(), function(layerFeature){
            return _.some(selectedFeatures, function(selectedFeature) {
              return me.geometryAndValuesEqual(selectedFeature.values_, layerFeature.values_);
            });
          });

          _.each(currentFeatures, me.removeFeature);

          if(selectedLinearAsset.isSplitOrSeparated() || _.some(linearAssets, function(asset){return !_.isEqual(asset.sideCode, 1);})){
            selectedFeatures = selectedFeatures.concat(assetLabel.renderFeaturesByLinearAssets(_.map(_.cloneDeep(linearAssets), me.offsetBySideCode), me.uiState.zoomLevel));
          }else
            selectedFeatures = selectedFeatures.concat(assetLabel.renderFeaturesByLinearAssets(linearAssets, me.uiState.zoomLevel));
        }
      }

      me.vectorSource.addFeatures(selectedFeatures);
      me.selectToolControl.addSelectionFeatures(selectedFeatures);

      if (selectedLinearAsset.isSplitOrSeparated()) {
        me.drawIndicators(_.map(_.cloneDeep(selectedLinearAsset.get()), me.offsetBySideCode));
      }
    }
  };

  var reset = function() {
    linearAssetCutter.deactivate();
  };

  this.showLayer = function(map) {
    startListeningExtraEvents();
    me.vectorLayer.setVisible(true);
    me.indicatorLayer.setVisible(true);
    roadAddressInfoPopup.start();
    me.show(map);
  };

  var showWithComplementary = function() {
    if(trafficSignReadOnlyLayer)
      trafficSignReadOnlyLayer.showTrafficSignsComplementary();
    isComplementaryChecked = true;
    me.readOnlyLayer.showWithComplementary();
    me.refreshView();
  };

  var hideComplementary = function() {
    if(trafficSignReadOnlyLayer)
      trafficSignReadOnlyLayer.hideTrafficSignsComplementary();
    me.selectToolControl.clear();
    selectedLinearAsset.close();
    isComplementaryChecked = false;
    me.readOnlyLayer.hideComplementary();
    roadAddressInfoPopup.stop();
    me.refreshView();
  };

  var showWalkingCycling = function () {
    collection.activeWalkingCycling(true);
    me.refreshView();
  };

  var hideWalkingCycling = function () {
    me.selectToolControl.clear();
    selectedLinearAsset.close();
    collection.activeWalkingCycling(false);
    roadAddressInfoPopup.stop();
    me.refreshView();
  };

  var showGeometry = function () {
    collection.activeGeometry(true);
    me.showLayer(map);
    me.refreshView();
  };

  var hideGeometry = function () {
    me.selectToolControl.clear();
    collection.activeGeometry(false);
    roadAddressInfoPopup.stop();
    me.hideLayer();
    me.refreshView();
  };

  eventbus.on( layerName + '-mapViewOnly:show', showGeometry);
  eventbus.on( layerName  + '-mapViewOnly:hide', hideGeometry);

  this.hideReadOnlyLayer = function(){
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

  this.highLightReadOnlyLayer = function(){
    if(trafficSignReadOnlyLayer){
      trafficSignReadOnlyLayer.highLightLayer();
    }
  };

  this.hideLayer = function() {
    reset();
    me.hideReadOnlyLayer();
    me.vectorLayer.setVisible(false);
    me.indicatorLayer.setVisible(false);
    me.readOnlyLayer.hideLayer();
    selectedLinearAsset.close();
    me.stopListeningExtraEvents();
    me.stop();
    me.hide();
  };

  var refreshSelectedView = function(){
    if(applicationModel.getSelectedLayer() == layerName)
      me.refreshView();
  };

  return {
    vectorLayer: me.vectorLayer,
    show: me.showLayer,
    hide: me.hideLayer,
    minZoomForContent: me.minZoomForContent
  };
};
})(this);