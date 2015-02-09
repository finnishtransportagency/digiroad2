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

    var createStrokeDashStyles = function() {
      var strokeDashStyles = {
        9: '1 6',
        10: '1 10',
        11: '1 18',
        12: '1 32',
        13: '1 32',
        14: '1 32',
        15: '1 32'
      };
      return _.flatten(_.map(strokeDashStyles, function(width, zoomLevel) {
        return _.map([2, 4, 6, 8], function(functionalClass) {
          return dashedStrokeWidthStyle(parseInt(zoomLevel, 10), functionalClass, {
            strokeWidth: width - 2,
                 strokeColor: '#ffffff',
                 strokeLinecap: 'square',
                 strokeDashstyle: strokeDashStyles[parseInt(zoomLevel, 10)],
                 strokeOpacity: 1
          });
        });
      }));
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
        },
        'link-type': {
          'default': new OpenLayers.StyleMap(),
          'select': new OpenLayers.StyleMap()
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
    functionalClassDefaultStyleMap.addUniqueValueRules('default', 'functionalClass', functionalClassColorLookup);
    functionalClassDefaultStyleMap.styles.default.addRules(createStrokeDashStyles());
    roadLayer.addUIStateDependentLookupToStyleMap(functionalClassDefaultStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(functionalClassDefaultStyleMap, 'default', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);

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
    functionalClassSelectionStyleMap.addUniqueValueRules('default', 'functionalClass', functionalClassColorLookup);
    functionalClassSelectionStyleMap.addUniqueValueRules('select', 'functionalClass', functionalClassColorLookup);
    functionalClassSelectionStyleMap.styles.select.addRules(createStrokeDashStyles());
    functionalClassSelectionStyleMap.styles.default.addRules(createStrokeDashStyles());
    roadLayer.addUIStateDependentLookupToStyleMap(functionalClassSelectionStyleMap, 'default', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(functionalClassSelectionStyleMap, 'select', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(functionalClassSelectionStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(functionalClassSelectionStyleMap, 'select', 'zoomLevel', oneWaySignSizeLookup);

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
