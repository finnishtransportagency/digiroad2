(function(root) {
  root.LinkPropertyLayer = function(map, roadLayer, geometryUtils, selectedLinkProperty, roadCollection, linkPropertiesModel) {
    var currentDataset;
    var currentRenderIntent;

    var functionalClassColorLookup = {
      1: { strokeColor: '#ff0000', externalGraphic: 'images/link-properties/functional-class-1.svg' },
      2: { strokeColor: '#ff0000', externalGraphic: 'images/link-properties/functional-class-2.svg' },
      3: { strokeColor: '#ff55dd', externalGraphic: 'images/link-properties/functional-class-3.svg' },
      4: { strokeColor: '#ff55dd', externalGraphic: 'images/link-properties/functional-class-4.svg' },
      5: { strokeColor: '#0011bb', externalGraphic: 'images/link-properties/functional-class-5.svg' },
      6: { strokeColor: '#0011bb', externalGraphic: 'images/link-properties/functional-class-6.svg' },
      7: { strokeColor: '#a4a4a2', externalGraphic: 'images/link-properties/functional-class-7.svg' },
      8: { strokeColor: '#a4a4a2', externalGraphic: 'images/link-properties/functional-class-8.svg' }
    };

    var oneWaySignSizeLookup = {
      9: { pointRadius: 0 },
      10: { pointRadius: 12 },
      11: { pointRadius: 14 },
      12: { pointRadius: 16 },
      13: { pointRadius: 20 },
      14: { pointRadius: 24 },
      15: { pointRadius: 24 }
    };

    var defaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      rotation: '${rotation}'
    }));

    var defaultStyleMap = new OpenLayers.StyleMap({ 'default': defaultStyle });

    var combineFilters = function(filters) {
      return new OpenLayers.Filter.Logical({ type: OpenLayers.Filter.Logical.AND, filters: filters });
    };

    var functionalClassFilter = function(functionalClass) {
      return new OpenLayers.Filter.Comparison({ type: OpenLayers.Filter.Comparison.EQUAL_TO, property: 'functionalClass', value: functionalClass });
    };

    var strokeWidthStyle = function(zoomLevel, functionalClass, symbolizer) {
      return new OpenLayers.Rule({
        filter: combineFilters([functionalClassFilter(functionalClass), roadLayer.createZoomLevelFilter(zoomLevel)]),
        symbolizer: symbolizer
      });
    };

    var createStrokeWidthStyles = function() {
      var strokeWidthsByZoomLevelAndFunctionalClass = {
        9:  [ 10, 10,  8,  8, 6, 6, 4, 4 ],
        10: [ 18, 18, 12, 12, 7, 7, 4, 4 ],
        11: [ 20, 20, 12, 12, 7, 7, 4, 4 ],
        12: [ 25, 25, 17, 17, 9, 9, 4, 4 ],
        13: [ 32, 32, 20, 20, 9, 9, 4, 4 ],
        14: [ 32, 32, 20, 20, 9, 9, 4, 4 ],
        15: [ 32, 32, 20, 20, 9, 9, 4, 4 ]
      };

      return _.chain(strokeWidthsByZoomLevelAndFunctionalClass).map(function(widthsByZoomLevel, zoomLevel) {
        return _.map(widthsByZoomLevel, function(width, index) {
          var functionalClass = index + 1;
          return strokeWidthStyle(parseInt(zoomLevel, 10), functionalClass, { strokeWidth: width });
        });
      }).flatten().value();
    };

    var createStrokeDashStyles = function() {
      return [new OpenLayers.Rule({
        filter: new OpenLayers.Filter.Comparison({ type: OpenLayers.Filter.Comparison.EQUAL_TO, property: 'type', value: 'overlay' }),
        symbolizer: {
          strokeColor: '#ffffff',
          strokeLinecap: 'square',
          strokeDashstyle: '1 32',
          strokeOpacity: 1
        }
      })];
    };

    var setDatasetSpecificStyleMap = function(dataset, renderIntent) {
      var getStyleMap = function(dataset, renderIntent) {
        var styleMaps = {
          'functional-class': {
            'default': defaultStyleMap,
            'select': selectionStyleMap
          },
          'administrative-class': {
            'default': administrativeClassDefaultStyleMap,
            'select': administrativeClassSelectionStyleMap
          }
        };
        return styleMaps[dataset][renderIntent];
      };

      if (dataset !== currentDataset || renderIntent !== currentRenderIntent) {
        roadLayer.setLayerSpecificStyleMap('linkProperties', getStyleMap(dataset, renderIntent));
        currentDataset = dataset;
        currentRenderIntent = renderIntent;
      }
    };

    roadLayer.addUIStateDependentLookupToStyleMap(defaultStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    defaultStyleMap.addUniqueValueRules('default', 'functionalClass', functionalClassColorLookup);
    defaultStyle.addRules(createStrokeWidthStyles());
    defaultStyle.addRules(createStrokeDashStyles());

    var selectionStyleMap = new OpenLayers.StyleMap({
      'select': new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeOpacity: 0.7,
        graphicOpacity: 1.0,
        rotation: '${rotation}'
      })),
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeOpacity: 0.3,
        graphicOpacity: 0.3,
        rotation: '${rotation}'
      }))
    });
    roadLayer.addUIStateDependentLookupToStyleMap(selectionStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(selectionStyleMap, 'select', 'zoomLevel', oneWaySignSizeLookup);
    selectionStyleMap.addUniqueValueRules('default', 'functionalClass', functionalClassColorLookup);
    selectionStyleMap.addUniqueValueRules('select', 'functionalClass', functionalClassColorLookup);
    selectionStyleMap.styles.select.addRules(createStrokeWidthStyles());
    selectionStyleMap.styles.default.addRules(createStrokeWidthStyles());

    var administrativeClassStyleLookup = {
      Private: { strokeColor: '#0011bb', externalGraphic: 'images/link-properties/privateroad.svg' },
      Municipality: { strokeColor: '#11bb00', externalGraphic: 'images/link-properties/street.svg' },
      State: { strokeColor: '#ff0000', externalGraphic: 'images/link-properties/road.svg' }
    };

    var administrativeClassDefaultStyleMap = new OpenLayers.StyleMap({
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeOpacity: 0.7,
        rotation: '${rotation}'
      }))
    });
    roadLayer.addUIStateDependentLookupToStyleMap(administrativeClassDefaultStyleMap, 'default', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(administrativeClassDefaultStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    administrativeClassDefaultStyleMap.addUniqueValueRules('default', 'administrativeClass', administrativeClassStyleLookup);

    var administrativeClassSelectionStyleMap = new OpenLayers.StyleMap({
      'select': new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeOpacity: 0.7,
        graphicOpacity: 1.0,
        rotation: '${rotation}'
      })),
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeOpacity: 0.3,
        graphicOpacity: 0.3,
        rotation: '${rotation}'
      }))
    });
    roadLayer.addUIStateDependentLookupToStyleMap(administrativeClassSelectionStyleMap, 'default', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(administrativeClassSelectionStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(administrativeClassSelectionStyleMap, 'select', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(administrativeClassSelectionStyleMap, 'select', 'zoomLevel', oneWaySignSizeLookup);
    administrativeClassSelectionStyleMap.addUniqueValueRules('default', 'administrativeClass', administrativeClassStyleLookup);
    administrativeClassSelectionStyleMap.addUniqueValueRules('select', 'administrativeClass', administrativeClassStyleLookup);

    setDatasetSpecificStyleMap('administrative-class', 'default');

    var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
      onSelect:  function(feature) {
        selectedLinkProperty.open(feature.attributes.roadLinkId);
        setDatasetSpecificStyleMap(currentDataset, 'select');
        roadLayer.layer.redraw();
        highlightFeatures(feature);
      },
      onUnselect: function() {
        deselectRoadLink();
        roadLayer.layer.redraw();
        highlightFeatures(null);
      }
    });
    map.addControl(selectControl);

    var eventListener = _.extend({running: false}, eventbus);

    var highlightFeatures = function(feature) {
      _.each(roadLayer.layer.features, function(x) {
        if (feature && (x.attributes.roadLinkId === feature.attributes.roadLinkId)) {
          selectControl.highlight(x);
        } else {
          selectControl.unhighlight(x);
        }
      });
    };

    var handleMapMoved = function(state) {
      if (zoomlevels.isInRoadLinkZoomLevel(state.zoom) && state.selectedLayer === 'linkProperties') {
        start();
      } else if (selectedLinkProperty.isDirty()) {
        displayConfirmMessage();
      } else {
        stop();
      }
    };

    var drawOneWaySigns = function(roadLinks) {
      var oneWaySigns = _.chain(roadLinks)
        .filter(function(link) {
          return link.trafficDirection === 'AgainstDigitizing' || link.trafficDirection === 'TowardsDigitizing';
        })
        .map(function(link) {
          var points = _.map(link.points, function(point) {
            return new OpenLayers.Geometry.Point(point.x, point.y);
          });
          var lineString = new OpenLayers.Geometry.LineString(points);
          var signPosition = geometryUtils.calculateMidpointOfLineString(lineString);
          var rotation = link.trafficDirection === 'AgainstDigitizing' ? signPosition.angleFromNorth + 180.0 : signPosition.angleFromNorth;
          var attributes = _.merge({}, link, { rotation: rotation });
          return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(signPosition.x, signPosition.y), attributes);
        })
        .value();

      roadLayer.layer.addFeatures(oneWaySigns);
    };

    var drawDashedLineFeatures = function(roadLinks) {
      var lineFeatures = function(roadLinks) {
        return _.flatten(_.map(roadLinks, function(roadLink) {
          var points = _.map(roadLink.points, function(point) {
            return new OpenLayers.Geometry.Point(point.x, point.y);
          });
          var attributes = {
            functionalClass: roadLink.functionalClass,
            type: 'overlay'
          };
          return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
        }));
      };

      var dashedFunctionalClasses = [2, 4, 6, 8];
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return _.contains(dashedFunctionalClasses, roadLink.functionalClass);
      });
      roadLayer.layer.addFeatures(lineFeatures(dashedRoadLinks));
    };

    var reselectRoadLink = function() {
      selectControl.activate();
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      var feature = _.find(roadLayer.layer.features, function(feature) { return feature.attributes.roadLinkId === selectedLinkProperty.getId(); });
      if (feature) {
        selectControl.select(feature);
        highlightFeatures(feature);
      }
      selectControl.onSelect = originalOnSelectHandler;
      if (selectedLinkProperty.get() && selectedLinkProperty.isDirty()) {
        selectControl.deactivate();
      }
    };

    var deselectRoadLink = function() {
      setDatasetSpecificStyleMap(currentDataset, 'default');
      selectedLinkProperty.close();
    };

    var prepareRoadLinkDraw = function() {
      selectControl.deactivate();
    };

    eventbus.on('map:moved', handleMapMoved);

    var start = function() {
      if (!eventListener.running) {
        eventListener.running = true;
        eventListener.listenTo(eventbus, 'roadLinks:beforeDraw', prepareRoadLinkDraw);
        eventListener.listenTo(eventbus, 'roadLinks:afterDraw', function(roadLinks) {
          if (currentDataset === 'functional-class') {
            drawDashedLineFeatures(roadLinks);
          }
          drawOneWaySigns(roadLinks);
          reselectRoadLink();
        });
        eventListener.listenTo(eventbus, 'linkProperties:changed', handleLinkPropertyChanged);
        eventListener.listenTo(eventbus, 'linkProperties:cancelled linkProperties:saved', concludeLinkPropertyEdit);
        eventListener.listenTo(eventbus, 'linkProperties:selected', function(link) {
          var feature = _.find(roadLayer.layer.features, function(feature) {
            return feature.attributes.roadLinkId === link.roadLinkId;
          });
          selectControl.select(feature);
        });
        eventListener.listenTo(eventbus, 'linkProperties:dataset:changed', function (dataset) {
          if (dataset !== currentDataset) {
            setDatasetSpecificStyleMap(dataset, currentRenderIntent);
            if (currentDataset === 'functional-class') {
              drawDashedLineFeatures(roadCollection.getAll());
            } else {
              roadLayer.layer.removeFeatures(roadLayer.layer.getFeaturesByAttribute('type', 'overlay'));
            }
            roadLayer.layer.redraw();
          }
        });
        selectControl.activate();
        setDatasetSpecificStyleMap(linkPropertiesModel.getDataset(), currentRenderIntent);
      }
    };


    var displayConfirmMessage = function() { new Confirm(); };

    var handleLinkPropertyChanged = function() {
      redrawSelected();
      selectControl.deactivate();
      eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
      eventListener.listenTo(eventbus, 'map:clicked', displayConfirmMessage);
    };

    var concludeLinkPropertyEdit = function() {
      selectControl.activate();
      eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
      redrawSelected();
    };

    var redrawSelected = function() {
      var selectedFeatures = _.filter(roadLayer.layer.features, function(feature) {
        return feature.attributes.roadLinkId === selectedLinkProperty.getId();
      });
      roadLayer.layer.removeFeatures(selectedFeatures);
      var data = selectedLinkProperty.get().getData();
      roadLayer.drawRoadLink(data);
      drawOneWaySigns([data]);
      reselectRoadLink();
    };

    var stop = function() {
      selectControl.deactivate();
      eventListener.stopListening(eventbus);
      eventListener.running = false;
    };

    var show = function(map) {
      if (zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
        start();
      }
    };

    var hide = function() {
      stop();
      deselectRoadLink();
    };

    return {
      show: show,
      hide: hide,
      minZoomForContent: zoomlevels.minZoomForRoadLinks
    };
  };
})(this);
