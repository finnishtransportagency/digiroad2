(function(root) {
  root.LinkPropertyLayerStyles = function(roadLayer) {
    var combineFilters = function(filters) {
      return new OpenLayers.Filter.Logical({ type: OpenLayers.Filter.Logical.AND, filters: filters });
    };

    var functionalClassFilter = function(functionalClass) {
      return new OpenLayers.Filter.Comparison({ type: OpenLayers.Filter.Comparison.EQUAL_TO, property: 'functionalClass', value: functionalClass });
    };

    var dashedStrokeWidthStyle = function(zoomLevel, functionalClass, symbolizer) {
      var overlayTypeFilter = new OpenLayers.Filter.Comparison({ type: OpenLayers.Filter.Comparison.EQUAL_TO, property: 'type', value: 'overlay' });
      return new OpenLayers.Rule({
        filter: combineFilters([overlayTypeFilter, functionalClassFilter(functionalClass), roadLayer.createZoomLevelFilter(zoomLevel)]),
        symbolizer: symbolizer
      });
    };

    var strokeWidthStyle = function(zoomLevel, functionalClass, symbolizer) {
      return new OpenLayers.Rule({
        filter: combineFilters([functionalClassFilter(functionalClass), roadLayer.createZoomLevelFilter(zoomLevel)]),
        symbolizer: symbolizer
      });
    };

    var strokeWidthsByZoomLevelAndFunctionalClass = {
      9: [ 10, 10, 8, 8, 6, 6, 4, 4 ],
      10: [ 18, 18, 12, 12, 7, 7, 4, 4 ],
      11: [ 20, 20, 12, 12, 7, 7, 4, 4 ],
      12: [ 25, 25, 17, 17, 9, 9, 4, 4 ],
      13: [ 32, 32, 20, 20, 9, 9, 4, 4 ],
      14: [ 32, 32, 20, 20, 9, 9, 4, 4 ],
      15: [ 32, 32, 20, 20, 9, 9, 4, 4 ]
    };

    var createStrokeWidthStyles = function() {
      return _.chain(strokeWidthsByZoomLevelAndFunctionalClass).map(function(widthsByZoomLevel, zoomLevel) {
        return _.map(widthsByZoomLevel, function(width, index) {
          var functionalClass = index + 1;
          return strokeWidthStyle(parseInt(zoomLevel, 10), functionalClass, { strokeWidth: width });
        });
      }).flatten().value();
    };

    var createStrokeDashStyles = function() {
      var strokeDashStyles = {
        9: {
          2: '1 32',
          4: '1 20',
          6: '1 10',
          8: '1 4'
        },
        10: {
          2: '1 38',
          4: '1 22',
          6: '1 12',
          8: '1 6'
        },
        11: {
          2: '1 46',
          4: '1 24',
          6: '1 16',
          8: '1 7'
        },
        12: {
          2: '1 56',
          4: '1 36',
          6: '1 18',
          8: '1 8'
        },
        13: {
          2: '1 74',
          4: '1 42',
          6: '1 20',
          8: '1 9'
        },
        14: {
          2: '1 74',
          4: '1 42',
          6: '1 20',
          8: '1 9'
        },
        15: {
          2: '1 74',
          4: '1 42',
          6: '1 20',
          8: '1 9'
        }
      };
      return _.chain(strokeWidthsByZoomLevelAndFunctionalClass).map(function(widthsByZoomLevel, zoomLevel) {
        return _.map(widthsByZoomLevel, function(width, index) {
          var functionalClass = index + 1;
          return dashedStrokeWidthStyle(parseInt(zoomLevel, 10), functionalClass, {
            strokeWidth: width - 2,
            strokeColor: '#ffffff',
            strokeLinecap: 'square',
            strokeDashstyle: strokeDashStyles[parseInt(zoomLevel, 10)][functionalClass] || '',
            strokeOpacity: 1
          });
        });
      }).flatten().value();
    };

    var getDatasetSpecificStyleMap = function(dataset, renderIntent) {
      var styleMaps = {
        'functional-class': {
          'default': functionalClassDefaultStyleMap,
          'select': functionalClassSelectionStyleMap
        },
        'administrative-class': {
          'default': administrativeClassDefaultStyleMap,
          'select': administrativeClassSelectionStyleMap
        }
      };
      return styleMaps[dataset][renderIntent];
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

    var administrativeClassStyleLookup = {
      Private: { strokeColor: '#0011bb', externalGraphic: 'images/link-properties/privateroad.svg' },
      Municipality: { strokeColor: '#11bb00', externalGraphic: 'images/link-properties/street.svg' },
      State: { strokeColor: '#ff0000', externalGraphic: 'images/link-properties/road.svg' }
    };


    // --- Functional class style maps

    var functionalClassDefaultStyleMap = new OpenLayers.StyleMap({
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeOpacity: 0.7,
        rotation: '${rotation}'}))
      });
    roadLayer.addUIStateDependentLookupToStyleMap(functionalClassDefaultStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    functionalClassDefaultStyleMap.addUniqueValueRules('default', 'functionalClass', functionalClassColorLookup);
    functionalClassDefaultStyleMap.styles.default.addRules(createStrokeWidthStyles());
    functionalClassDefaultStyleMap.styles.default.addRules(createStrokeDashStyles());

    var functionalClassSelectionStyleMap = new OpenLayers.StyleMap({
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
    roadLayer.addUIStateDependentLookupToStyleMap(functionalClassSelectionStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(functionalClassSelectionStyleMap, 'select', 'zoomLevel', oneWaySignSizeLookup);
    functionalClassSelectionStyleMap.addUniqueValueRules('default', 'functionalClass', functionalClassColorLookup);
    functionalClassSelectionStyleMap.addUniqueValueRules('select', 'functionalClass', functionalClassColorLookup);
    functionalClassSelectionStyleMap.styles.select.addRules(createStrokeWidthStyles());
    functionalClassSelectionStyleMap.styles.default.addRules(createStrokeWidthStyles());


    // --- Administrative class style maps ---

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

    return {
      getDatasetSpecificStyleMap: getDatasetSpecificStyleMap
    };
  };
})(this);
