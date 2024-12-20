(function(root) {
  root.LaneModellingLayer  = function(params) {
    var map = params.map,
      application = params.application,
      collection = params.collection,
      selectedLane = params.selectedLinearAsset,
      style = params.style,
      assetLabel = params.assetLabel,
      authorizationPolicy = params.authorizationPolicy,
      laneReadOnlyLayer = params.laneReadOnlyLayer;

    LinearAssetLayer.call(this, params);
    var me = this;

    this.readOnlyLayer = laneReadOnlyLayer(params, map);
    me.vectorLayer.setZIndex(2);


    var cutToolAlertPopUpOptions = {
      type: "alert",
      yesButtonLbl: 'Ok',
    };
    var alertMessageMainLanePromotion = "Kaistan katkaisu ei ole sallittu, kun lisäkaista on muutettu pääkaistaksi. Tallenna pääkaistan paikan muutos ensin.";
    var alertMessageMultipleLinksSelected = "Kaistan katkaisu ei ole sallittu, kun useamman kuin yhden tielinkin kaistat ovat valittu";

    var LinearAssetCutter = function(eventListener, vectorLayer) {
      var scissorFeatures = [];
      //Max euclidean distance in geometry points allowed between mouse point and the closest linear asset point (the planned cut point).
      var CUT_THRESHOLD = 5;
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

            var uniqueSelectedRoadLinkIds = _.uniq(_.map(properties.selectedLinks, function (selectedLink) {
              return selectedLink.linkId;
            }));
            if(_.isUndefined(uniqueSelectedRoadLinkIds)) {
              return true;
            }

            var SelectedLaneCannotBeCut = !selectedLane.isOuterLane(laneNumber) || laneNumber == 1 || laneNumber != selectedLane.getCurrentLaneNumber() ||
                uniqueSelectedRoadLinkIds.length > 1 || selectedLane.isAddByRoadAddress();

            return SelectedLaneCannotBeCut;
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

      var getLanePieceToCut = function (point) {

        var minSquaredDistanceBetweenPointAndLane = function (lane, point) {
          var min_x_dist = Math.abs(lane.points[0].x - point[0]);
          var min_y_dist = Math.abs(lane.points[0].y - point[1]);
          for(var i=1; i < lane.points.length; i++) {
            min_x_dist = Math.min(min_x_dist, Math.abs(lane.points[i].x - point[0]));
            min_y_dist = Math.min(min_y_dist, Math.abs(lane.points[i].y - point[1]));
          }
          return Math.pow(min_x_dist, 2) + Math.pow(min_y_dist, 2);
        };

        var selected = selectedLane.get();

        selected = _.reject(selected, function (lane) {
          var laneNumber = _.head(_.find(lane.properties, function(property){
            return property.publicId === "lane_code";
          }).values).value;

          return !selectedLane.isOuterLane(laneNumber) || laneNumber == 1 ||
              laneNumber != selectedLane.getCurrentLaneNumber() || selectedLane.isAddByRoadAddress();
        });

        selected = _.sortBy(selected, function (lane) {
          return minSquaredDistanceBetweenPointAndLane(lane, point);
        });

        var cutLanes = _.reject(selected, function (lane) {
          return _.isUndefined(lane.marker);
        });

        if (_.isEmpty(cutLanes)) {
          return _.head(selected);
        }

        return _.head(cutLanes);
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

        var nearestLinearAsset = getLanePieceToCut([mousePoint.x, mousePoint.y]);
        if (_.isUndefined(nearestLinearAsset)) return;

        if (selectedLane.promotionDirty) {
          GenericConfirmPopup(alertMessageMainLanePromotion, cutToolAlertPopUpOptions);
          return;
        }

        if (nearestLinearAsset.selectedLinks.length !== 1) {
          GenericConfirmPopup(alertMessageMultipleLinksSelected, cutToolAlertPopUpOptions);
          return;
        }

        if(authorizationPolicy.formEditModeAccess(nearestLinearAsset)) {
          var splitProperties = calculateSplitProperties(nearestLinearAsset, mousePoint);
          selectedLane.splitLinearAsset(_.head(_.find(nearestLinearAsset.properties, function(property){
            return property.publicId === "lane_code";
          }).values).value, splitProperties, nearestLinearAsset.marker);

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
      eventListener.listenTo(eventbus, me.singleElementEvents('viewOnlyAsset'), me.refreshViewOnlyLayer);
    };

    this.refreshViewOnlyLayer = function () {
      me.readOnlyLayer.refreshView();
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

    /*
    Seems not possible to use the same behaviour as changeTool
     */
    var handleLinearAssetChanged = function(eventListener, selectedLinearAsset, laneNumber) {
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      eventListener.stopListening(eventbus, 'map:clicked', selectedLinearAsset.cancel);

      if (selectedLinearAsset.isDirty() && application.getSelectedTool() !== 'Cut') {
        me.selectToolControl.deactivate();
        eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
      }else if(!selectedLinearAsset.isDirty() && application.getSelectedTool() !== 'Cut'){
        eventListener.listenTo(eventbus, 'map:clicked', selectedLinearAsset.cancel);
      }

      me.decorateSelection(laneNumber);
    };

    this.refreshView = function() {
      me.vectorLayer.setVisible(true);
      me.adjustStylesByZoomLevel(zoomlevels.getViewZoom(map));

      collection.fetch(map.getView().calculateExtent(map.getSize()), map.getView().getCenter(), Math.round(map.getView().getZoom())).then(function() {
        if (!selectedLane.exists())
          eventbus.trigger(me.singleElementEvents('viewOnlyAsset'));
        else
          me.readOnlyLayer.hideLayer();
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

      var drawSplitPoints = function (links) {

        function findSplitPoints(links) {
          var sortedPoints = _.flatMap(links, function (link) {
            return link.points;
          }).sort(function (a, b) {
            return a.x - b.x;
          });
          var foundSplitPoints = [];
          for(var i = 1; i < sortedPoints.length; i++) {
            var distance = Math.sqrt(Math.pow((sortedPoints[i].x - sortedPoints[i - 1].x), 2) + Math.pow((sortedPoints[i].y - sortedPoints[i - 1].y), 2));
            if (distance < 0.001) {
              foundSplitPoints.push(sortedPoints[i]);
            }
          }
          return foundSplitPoints;
        }
        var splitPoints = findSplitPoints(links);

        var style = new ol.style.Style({
          image : new ol.style.Icon({src: 'images/center-marker-red.svg'}),
          text : new ol.style.Text({text : "X", fill: new ol.style.Fill({color: '#ffffff'}), font : '12px sans-serif'})
        });

        splitPoints.forEach(function (point) {
          var splitMarker = new ol.Feature({
            geometry : new ol.geom.Point([point.x, point.y])
          });
          splitMarker.setStyle(style);
          features.push(splitMarker);
        });
      };

      var indicatorsForSplit = function() {
        me.mapOverLinkMiddlePoints(links, function(link, middlePoint) {
          markerContainer(link, middlePoint);
        });
        drawSplitPoints(links);
      };

      indicatorsForSplit();
      me.selectToolControl.addNewFeature(features);
    };

    function getAllLanesToSameArray(links) {
      var linkIds = _.uniq(_.map(links, function(link) {
        return link.values_.linkId;
      }));
      var modifiedLinks = [];
      linkIds.forEach(function (linkId) {
        var lanesOnSameLink = _.filter(links, function (link) {
          return link.values_.linkId === linkId;
        });
        if (!_.isEmpty(lanesOnSameLink)) {
          var uniqueLanes = _.uniq(_.map(lanesOnSameLink, function(link) {
            return parseInt(link.values_.lanes[0]);
          }));
          lanesOnSameLink[0].values_.lanes = uniqueLanes;
          modifiedLinks.push(lanesOnSameLink[0]);
        }
      });
      return modifiedLinks;
    }

    function getOneWaySigns(links) {
      var filteredLinks = _.filter(links, function(link) {
        return link.trafficDirection === 'AgainstDigitizing' || link.trafficDirection === 'TowardsDigitizing';
      });
      var oneWaySigns = me.mapOverLinkMiddlePoints(filteredLinks, function(link, middlePoint) {
        var rotation = link.trafficDirection === 'AgainstDigitizing' ? middlePoint.angleFromNorth + Math.PI : middlePoint.angleFromNorth;
        var attributes = _.merge({}, link, { rotation: rotation  });
        return new ol.Feature(_.merge(attributes,{ geometry: new ol.geom.Point([middlePoint.x, middlePoint.y])}));
      });
      return oneWaySigns;
    }

    var redrawLinearAssets = function(linearAssetChains) {
      me.vectorSource.clear();
      me.indicatorLayer.getSource().clear();
      me.decorateSelection(selectedLane.exists() ? selectedLane.getCurrentLaneNumber() : undefined);
      me.drawLinearAssets(linearAssetChains);
    };

    this.drawLinearAssets = function(linearAssetChains) {
      var linearAssets = _.flatten(linearAssetChains);
      var allButSelected = _.filter(linearAssets, function(asset){ return !_.some(selectedLane.get(), function(selectedAsset){
        return selectedAsset.linkId === asset.linkId && selectedAsset.sideCode == asset.sideCode &&
            selectedAsset.startMeasure === asset.startMeasure && selectedAsset.endMeasure === asset.endMeasure; }) ;
      });
      me.vectorSource.addFeatures(style.renderFeatures(allButSelected));
      var oneWaySignsDraft = getOneWaySigns(allButSelected);
      var oneWaySigns = getAllLanesToSameArray(oneWaySignsDraft);
      me.vectorSource.addFeatures(oneWaySigns);
      me.readOnlyLayer.showLayer();
      me.highLightReadOnlyLayer();
    };

    var offsetByLaneNumber = function (linearAsset) {
      return laneUtils.offsetByLaneNumber(linearAsset);
    };

    var extractMiddleLinksOfChains = function (linearAssetChains, linksToConsider) {
      return _.flatMap(linearAssetChains, function (chain) {
        var  links = _.isEmpty(linksToConsider) ? chain : _.intersectionWith(chain, linksToConsider, _.isEqual);
        return _.flatMap(_.groupBy(links, 'roadPartNumber'), function (chainByRoadPartNumber) {
          var minAddressMValue = _.minBy(chainByRoadPartNumber, 'startAddrMValue');
          var maxAddressMValue = _.maxBy(chainByRoadPartNumber, 'endAddrMValue');
          if(minAddressMValue && maxAddressMValue ) {
            var middleAddressMValue = (maxAddressMValue.endAddrMValue - minAddressMValue.startAddrMValue) / 2 + minAddressMValue.startAddrMValue;
            return _.filter(chainByRoadPartNumber, function (linearAsset) {
              return linearAsset.startAddrMValue <= middleAddressMValue && linearAsset.endAddrMValue > middleAddressMValue;
            });
          }
          else{
            return chainByRoadPartNumber;
          }
        });
      });
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

          selectedFeatures = selectedFeatures.concat(assetLabel.renderFeaturesByLinearAssets(extractMiddleLinksOfChains([_.map(selectedFeatures, function (feature) {
            return feature.getProperties();
          })]), me.uiState.zoomLevel));
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
        var oneWaySignForSelection = getOneWaySigns(linearAssets);
        if (oneWaySignForSelection.length > 0) {
          oneWaySignForSelection[0].values_.isSelected = true;
          me.vectorSource.addFeatures(oneWaySignForSelection);
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