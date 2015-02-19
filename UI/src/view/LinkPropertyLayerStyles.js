(function(root) {
  root.LinkPropertyLayerStyles = function(roadLayer) {
    var combineFilters = function(filters) {
      return new OpenLayers.Filter.Logical({ type: OpenLayers.Filter.Logical.AND, filters: filters });
    };

    var typeFilter = function(type) {
      return new OpenLayers.Filter.Comparison({ type: OpenLayers.Filter.Comparison.EQUAL_TO, property: 'type', value: type });
    };

    var createStrokeDashStyle = function(zoomLevel, style) {
      return new OpenLayers.Rule({
        filter: combineFilters([typeFilter('overlay'), roadLayer.createZoomLevelFilter(zoomLevel)]),
        symbolizer: style
      });
    };

    var overlayStrokeDashStyleRules = [
      createStrokeDashStyle(9,  { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 6' }),
      createStrokeDashStyle(10, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 3, strokeDashstyle: '1 10' }),
      createStrokeDashStyle(11, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 7, strokeDashstyle: '1 18' }),
      createStrokeDashStyle(12, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 14, strokeDashstyle: '1 32' }),
      createStrokeDashStyle(13, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 14, strokeDashstyle: '1 32' }),
      createStrokeDashStyle(14, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 14, strokeDashstyle: '1 32' }),
      createStrokeDashStyle(15, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 14, strokeDashstyle: '1 32' })
    ];

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
          'default': linkTypeDefaultStyleMap,
          'select': linkTypeSelectionStyleMap
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
      Private: { strokeColor: '#0011bb', externalGraphic: 'images/link-properties/arrow-blue.svg' },
      Municipality: { strokeColor: '#11bb00', externalGraphic: 'images/link-properties/arrow-green.svg' },
      State: { strokeColor: '#ff0000', externalGraphic: 'images/link-properties/arrow-red.svg' },
      Unknown: { strokeColor: '#888', externalGraphic: 'images/link-properties/arrow-grey.svg' }
    };

    // --- Functional class style maps

    var functionalClassDefaultStyleMap = new OpenLayers.StyleMap({
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeOpacity: 0.7,
        rotation: '${rotation}'}))
      });
    functionalClassDefaultStyleMap.addUniqueValueRules('default', 'functionalClass', functionalClassColorLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(functionalClassDefaultStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(functionalClassDefaultStyleMap, 'default', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    functionalClassDefaultStyleMap.styles.default.addRules(overlayStrokeDashStyleRules);

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
    roadLayer.addUIStateDependentLookupToStyleMap(functionalClassSelectionStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(functionalClassSelectionStyleMap, 'select', 'zoomLevel', oneWaySignSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(functionalClassSelectionStyleMap, 'default', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(functionalClassSelectionStyleMap, 'select', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    functionalClassSelectionStyleMap.styles.select.addRules(overlayStrokeDashStyleRules);
    functionalClassSelectionStyleMap.styles.default.addRules(overlayStrokeDashStyleRules);


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

    // --- Link type style maps

    var linkTypeColorLookup = {
      1: { strokeColor: '#ff0000',  externalGraphic: 'images/link-properties/arrow-red.svg' },
      2: { strokeColor: '#0011bb',  externalGraphic: 'images/link-properties/arrow-blue.svg' },
      3: { strokeColor: '#0011bb',  externalGraphic: 'images/link-properties/arrow-blue.svg' },
      4: { strokeColor: '#ff0000',  externalGraphic: 'images/link-properties/arrow-red.svg' },
      5: { strokeColor: '#00ccdd',  externalGraphic: 'images/link-properties/arrow-cyan.svg' },
      6: { strokeColor: '#00ccdd',  externalGraphic: 'images/link-properties/arrow-cyan.svg' },
      7: { strokeColor: '#11bb00',  externalGraphic: 'images/link-properties/arrow-green.svg' },
      8: { strokeColor: '#888',     externalGraphic: 'images/link-properties/arrow-grey.svg' },
      9: { strokeColor: '#888',     externalGraphic: 'images/link-properties/arrow-grey.svg' },
      10: { strokeColor: '#11bb00', externalGraphic: 'images/link-properties/arrow-green.svg' },
      11: { strokeColor: '#11bb00', externalGraphic: 'images/link-properties/arrow-green.svg' },
      12: { strokeColor: '#11bb00', externalGraphic: 'images/link-properties/arrow-green.svg' },
      13: { strokeColor: '#ff55dd', externalGraphic: 'images/link-properties/arrow-pink.svg' },
      21: { strokeColor: '#ff55dd', externalGraphic: 'images/link-properties/arrow-pink.svg' }
    };

    var linkTypeDefaultStyleMap = new OpenLayers.StyleMap({
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeOpacity: 0.7,
        rotation: '${rotation}'}))
    });
    linkTypeDefaultStyleMap.addUniqueValueRules('default', 'linkType', linkTypeColorLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(linkTypeDefaultStyleMap, 'default', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(linkTypeDefaultStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    linkTypeDefaultStyleMap.styles.default.addRules(overlayStrokeDashStyleRules);

    var linkTypeSelectionStyleMap = new OpenLayers.StyleMap({
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
    linkTypeSelectionStyleMap.addUniqueValueRules('default', 'linkType', linkTypeColorLookup);
    linkTypeSelectionStyleMap.addUniqueValueRules('select', 'linkType', linkTypeColorLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(linkTypeSelectionStyleMap, 'default', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(linkTypeSelectionStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(linkTypeSelectionStyleMap, 'select', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(linkTypeSelectionStyleMap, 'select', 'zoomLevel', oneWaySignSizeLookup);
    linkTypeSelectionStyleMap.styles.default.addRules(overlayStrokeDashStyleRules);
    linkTypeSelectionStyleMap.styles.select.addRules(overlayStrokeDashStyleRules);

    return {
      getDatasetSpecificStyleMap: getDatasetSpecificStyleMap
    };
  };
})(this);
