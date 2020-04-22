(function(root) {
  root.LaneModellingLayer  = function(params) {
    var map = params.map,
      application = params.application,
      collection = params.collection,
      selectedLane = params.selectedLinearAsset,
      style = params.style,
      assetLabel = params.assetLabel,
      authorizationPolicy = params.authorizationPolicy;

    LinearAssetLayer.call(this, params);
    var me = this;

    var LinearAssetCutter = function(eventListener, vectorLayer) {
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
          self.cut(evt);
        }
      };

      this.deactivate = function() {
        eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
        eventListener.stopListening(eventbus, 'map:clicked', selectedLane.cancel);
        eventListener.stopListening(eventbus, 'map:clicked', clickHandler);
        eventListener.stopListening(eventbus, 'map:mouseMoved');
        remove();
      };

      this.activate = function() {
        eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
        eventListener.stopListening(eventbus, 'map:clicked', selectedLane.cancel);
        eventListener.listenTo(eventbus, 'map:clicked', clickHandler);
        eventListener.listenTo(eventbus, 'map:mouseMoved', function(event) {
          if (application.getSelectedTool() === 'Cut') {
            self.updateByPosition(event.coordinate);
          }
        });
      };

      var isWithinCutThreshold = function(linearAssetLink) {
        return linearAssetLink && linearAssetLink < CUT_THRESHOLD;
      };

      var findNearestLinearAssetLink = function(point) {
        var laneFeatures = _.reject(vectorSource.getFeatures(), function (feature) {
          return _.isUndefined(feature.values_.properties);
        });

        return _.chain(laneFeatures)
          .filter(function(feature) {
            return feature.getGeometry() instanceof ol.geom.LineString;
          })
          .reject(function(feature) {
            var properties = feature.getProperties();
            var laneNumber = _.head(_.find(properties.properties, function(property){
              return property.publicId === "lane_code";
            }).values).value;

            return !selectedLane.isOuterLane(laneNumber) || laneNumber.toString()[1] == "1" ||
              !_.isUndefined(properties.marker) || properties.selectedLinks.length > 1 || selectedLane.isAddByRoadAddress();
          })
          .map(function(feature) {
            var closestP = feature.getGeometry().getClosestPoint(point);
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

        if (_.isUndefined(nearest) || !isWithinCutThreshold(nearest.distance)) {
          return;
        }

        var nearestLinearAsset = nearest.feature.getProperties();
        if(authorizationPolicy.formEditModeAccess(nearestLinearAsset)) {
          var splitProperties = calculateSplitProperties(laneUtils.offsetByLaneNumber(nearestLinearAsset, false, true), mousePoint);
          selectedLane.splitLinearAsset(_.head(_.find(nearestLinearAsset.properties, function(property){
            return property.publicId === "lane_code";
          }).values).value, splitProperties);

          remove();
        }
      };
    };

    var linearAssetCutter = new LinearAssetCutter(me.eventListener, me.vectorLayer);

    this.onSelect = function(evt) {
      if(me.selectableZoomLevel()) {
        if(!_.isEmpty(evt.selected)) {
          var feature = evt.selected[0];
          var properties = feature.getProperties();
          verifyClickEvent(evt, properties);

        }else if (selectedLane.exists()) {
          selectedLane.close();
          me.readOnlyLayer.showLayer();
          me.highLightReadOnlyLayer();
        }
      }
    };

    var verifyClickEvent = function(evt, properties){
      var singleLinkSelect;
      if(evt) {
        singleLinkSelect = evt.mapBrowserEvent.type === 'dblclick';
      }

      selectedLane.open(properties, singleLinkSelect);
      me.highlightMultipleLinearAssetFeatures();
    };

    var changeTool = function(eventListener, tool) {
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
        default:
      }

      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      eventListener.stopListening(eventbus, 'map:clicked', selectedLane.cancel);

      if (selectedLane.isDirty() && application.getSelectedTool() !== 'Cut') {
        eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
      }else if(application.getSelectedTool() !== 'Cut'){
        eventListener.listenTo(eventbus, 'map:clicked', selectedLane.cancel);
      }
    };

    this.bindEvents = function(eventListener) {
      var linearAssetChanged = _.partial(handleLinearAssetChanged, eventListener);
      var linearAssetCancelled = _.partial(handleLinearAssetCancelled, eventListener);
      var linearAssetUnSelected = _.partial(handleLinearAssetUnSelected, eventListener);
      var switchTool = _.partial(changeTool, eventListener);

      eventListener.listenTo(eventbus, me.singleElementEvents('unselect'), linearAssetUnSelected);
      eventListener.listenTo(eventbus, me.singleElementEvents('selected','multiSelected'), me.linearAssetSelected);
      eventListener.listenTo(eventbus, me.multiElementEvent('fetched'), redrawLinearAssets);
      eventListener.listenTo(eventbus, 'tool:changed', switchTool);
      eventListener.listenTo(eventbus, me.singleElementEvents('saved'), me.handleLinearAssetSaved);
      eventListener.listenTo(eventbus, me.multiElementEvent('massUpdateSucceeded'), me.handleLinearAssetSaved);
      eventListener.listenTo(eventbus, me.singleElementEvents('valueChanged', 'separated'), linearAssetChanged);
      eventListener.listenTo(eventbus, me.singleElementEvents('cancelled', 'saved'), linearAssetCancelled);
      eventListener.listenTo(eventbus, me.multiElementEvent('cancelled'), linearAssetCancelled);
      eventListener.listenTo(eventbus, me.singleElementEvents('selectByLinkId'), selectLinearAssetByLinkId);
      eventListener.listenTo(eventbus, me.multiElementEvent('massUpdateFailed'), me.cancelSelection);
      eventListener.listenTo(eventbus, me.multiElementEvent('valueChanged'), linearAssetChanged);
      eventListener.listenTo(eventbus, me.singleElementEvents('linearAsset'), me.refreshReadOnlyLayer);
    };

    var selectLinearAssetByLinkId = function(linkId) {
      var feature = _.find(me.vectorLayer.features, function(feature) { return feature.attributes.linkId === linkId; });
      if (feature) {
        me.selectToolControl.addSelectionFeatures([feature]);
      }
    };

    var handleLinearAssetUnSelected = function (eventListener) {
      changeTool(eventListener, application.getSelectedTool());
      me.eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
    };

    var handleLinearAssetChanged = function(eventListener, selectedLinearAsset, laneNumber) {
      changeTool(eventListener, application.getSelectedTool());
      me.decorateSelection(laneNumber);
    };

    this.refreshView = function() {
      me.vectorLayer.setVisible(true);
      me.adjustStylesByZoomLevel(zoomlevels.getViewZoom(map));

      collection.fetch(map.getView().calculateExtent(map.getSize()), map.getView().getCenter(), Math.round(map.getView().getZoom())).then(function() {
        eventbus.trigger(me.singleElementEvents('linearAsset'));
      });
    };

    var handleLinearAssetCancelled = function(eventListener) {
      me.selectToolControl.clear();
      changeTool(eventListener, application.getSelectedTool());

      redrawLinearAssets(collection.getAll());
    };

    this.drawIndicators = function(links) {
      var features = [];

      var markerContainer = function(link, position) {
        var imageSettings = {src: 'images/center-marker2.svg'};

        var textSettings = {
          text : link.marker,
          fill: new ol.style.Fill({
            color: '#ffffff'
          }),
          font : '12px sans-serif'
        };

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

      indicatorsForSplit();
      me.selectToolControl.addNewFeature(features);
    };

    var redrawLinearAssets = function(linearAssetChains) {
      me.vectorSource.clear();
      me.indicatorLayer.getSource().clear();
      var linearAssets = _.flatten(linearAssetChains);
      me.decorateSelection(selectedLane.exists() ? selectedLane.getCurrentLaneNumber() : undefined);
      me.drawLinearAssets(linearAssets, me.vectorSource);
    };

    this.drawLinearAssets = function(linearAssets) {
      var allButSelected = _.filter(linearAssets, function(asset){ return !_.some(selectedLane.get(), function(selectedAsset){
        return selectedAsset.linkId === asset.linkId && selectedAsset.sideCode == asset.sideCode &&
          selectedAsset.startMeasure === asset.startMeasure && selectedAsset.endMeasure === asset.endMeasure; }) ;
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

    var offsetByLaneNumber = function (linearAsset) {
      return laneUtils.offsetByLaneNumber(linearAsset);
    };

    this.decorateSelection = function (laneNumber) {
      function removeOldAssetFeatures() {
        var features = _.reject(me.vectorSource.getFeatures(), function (feature) {
          return _.isUndefined(feature.values_.properties);
        });

        _.forEach(features, function (feature) {
          me.vectorSource.removeFeature(feature);
        });
      }

      if (selectedLane.exists()) {
        var linearAssets = selectedLane.get();
        var selectedFeatures = style.renderFeatures(linearAssets, laneNumber);

        if (assetLabel) {
            var currentFeatures = _.filter(me.vectorSource.getFeatures(), function (layerFeature) {
              return _.some(selectedFeatures, function (selectedFeature) {
                return me.geometryAndValuesEqual(selectedFeature.values_, layerFeature.values_);
              });
            });

            _.each(currentFeatures, me.removeFeature);

              selectedFeatures = selectedFeatures.concat(assetLabel.renderFeaturesByLinearAssets(_.map(selectedFeatures, function (feature) {
                return feature.values_;
              }), me.uiState.zoomLevel));
        }

        removeOldAssetFeatures();
        me.vectorSource.addFeatures(selectedFeatures);
        me.selectToolControl.addSelectionFeatures(selectedFeatures);

        if(_.isUndefined(laneNumber))
          removeOldAssetFeatures();

        if (selectedLane.isSplit(laneNumber)) {
          me.drawIndicators(_.map(_.cloneDeep(_.filter(selectedLane.get(), function (lane){
            return _.find(lane.properties, function (property) {
              return property.publicId === "lane_code" && _.head(property.values).value == laneNumber;
            });
          })), offsetByLaneNumber));
        }
      }
    };

    var reset = function() {
      linearAssetCutter.deactivate();
    };

    this.hideLayer = function() {
      reset();
      me.selectToolControl.clear();
      me.hideReadOnlyLayer();
      me.vectorLayer.setVisible(false);
      me.indicatorLayer.setVisible(false);
      me.readOnlyLayer.hideLayer();
      selectedLane.close();
      me.stopListeningExtraEvents();
      me.stop();
      me.hide();
    };

    return {
      vectorLayer: me.vectorLayer,
      show: me.showLayer,
      hide: me.hideLayer,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);