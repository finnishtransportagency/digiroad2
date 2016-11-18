(function(root) {
  root.LinkPropertyLayerStyles = function(roadLayer) {

    var unknownRoadAddressAnomalyRules = [
      new OpenLayersRule().where('anomaly').is(1).use({ strokeColor: '#000000', strokeOpacity: 0.8, externalGraphic: 'images/speed-limits/unknown.svg', pointRadius: 14, graphicZIndex: 1})
    ];
    var unknownRoadAddressAnomalyUnselectedRules = [
      new OpenLayersRule().where('anomaly').is(1).use({ strokeColor: '#000000', strokeOpacity: 0.3, externalGraphic: 'images/speed-limits/unknown.svg', pointRadius: 14, graphicZIndex: 1})
    ];

    var FloatingRoadAddressAnomalyRules = [
      new OpenLayersRule().where('roadLinkType').is(-1).use({ strokeColor: '#F7FE2E ', strokeOpacity: 0.8, strokeWidth:20, pointRadius: 14})
    ];
    var FloatingRoadAddressAnomalyUnselectedRules = [
      new OpenLayersRule().where('roadLinkType').is(-1).use({ strokeColor: '#F7FE2E ', strokeOpacity: 0.3, strokeWidth:20, pointRadius: 14})
    ];

    var typeFilter = function(type) {
      return new OpenLayers.Filter.Comparison({ type: OpenLayers.Filter.Comparison.EQUAL_TO, property: 'type', value: type });
    };

    var zoomLevelFilter = function(zoomLevel) {
      return new OpenLayers.Filter.Function({ evaluate: function() { return applicationModel.zoom.level === zoomLevel; } });
    };

    var combineFilters = function(filters) {
      return new OpenLayers.Filter.Logical({ type: OpenLayers.Filter.Logical.AND, filters: filters });
    };

    var createZoomAndTypeDependentRule = function(type, zoomLevel, style) {
      return new OpenLayers.Rule({
        filter: combineFilters([typeFilter(type), zoomLevelFilter(zoomLevel)]),
        symbolizer: style
      });
    };

    var overlayStyleRule = _.partial(createZoomAndTypeDependentRule, 'overlay');
    var borderStyleRule = _.partial(createZoomAndTypeDependentRule, 'underlay');

    var zoomLevelRules = [
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(8).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[8])),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(9).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[9])),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(10).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[10])),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(11).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[11])),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(12).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[12])),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(13).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[13])),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(14).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[14])),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(15).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[15]))
    ];

    var overlayRules = [
      overlayStyleRule(9, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 6' }),
      overlayStyleRule(10, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 3, strokeDashstyle: '1 10' }),
      overlayStyleRule(11, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 5, strokeDashstyle: '1 15' }),
      overlayStyleRule(12, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
      overlayStyleRule(13, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
      overlayStyleRule(14, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' }),
      overlayStyleRule(15, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' })
      // new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 6' }),
      // new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 3, strokeDashstyle: '1 10' }),
      // new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 5, strokeDashstyle: '1 15' }),
      // new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).isIn([12, 13]).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
      // new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).isIn([14, 15]).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' })
    ];

    var borderRules = [
      borderStyleRule(9, { strokeColor: '#000000', strokeOpacity: 1.0, strokeLinecap: 'round', strokeWidth: 15, graphicZIndex: -1}),
      borderStyleRule(10, { strokeColor: '#000000', strokeOpacity: 1.0, strokeLinecap: 'round', strokeWidth: 15, graphicZIndex: -1}),
      borderStyleRule(11, { strokeColor: '#000000', strokeOpacity: 1.0, strokeLinecap: 'round', strokeWidth: 15, graphicZIndex: -1}),
      borderStyleRule(12, { strokeColor: '#000000', strokeOpacity: 1.0, strokeLinecap: 'round', strokeWidth: 15, graphicZIndex: -1}),
      borderStyleRule(13, { strokeColor: '#000000', strokeOpacity: 1.0, strokeLinecap: 'round', strokeWidth: 15, graphicZIndex: -1}),
      borderStyleRule(14, { strokeColor: '#000000', strokeOpacity: 1.0, strokeLinecap: 'round', strokeWidth: 15, graphicZIndex: -1}),
      borderStyleRule(15, { strokeColor: '#000000', strokeOpacity: 1.0, strokeLinecap: 'round', strokeWidth: 15, graphicZIndex: -1})
      // new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 6' }),
      // new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 3, strokeDashstyle: '1 10' }),
      // new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 5, strokeDashstyle: '1 15' }),
      // new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).isIn([12, 13]).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
      // new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).isIn([14, 15]).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' })
    ];

    var linkTypeSizeRules = [
      // new OpenLayersRule().where('linkType').isIn([8, 9, 12, 21]).use({ strokeWidth: 6 }),
      // new OpenLayersRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel', roadLayer.uiState).is(10).use({ strokeWidth: 2 }),
      // new OpenLayersRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel', roadLayer.uiState).is(11).use({ strokeWidth: 4 }),
      // new OpenLayersRule().where('type').is('overlay').and('linkType').isIn([8, 9, 12, 21]).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 4, strokeDashstyle: '1 16' }),
      // new OpenLayersRule().where('type').is('overlay').and('linkType').isIn([8, 9, 12, 21]).and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 8' }),
      // new OpenLayersRule().where('type').is('overlay').and('linkType').isIn([8, 9, 12, 21]).and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 2, strokeDashstyle: '1 8' })
    ];

    var overlayDefaultOpacity = [
      new OpenLayersRule().where('type').is('overlay').use({ strokeOpacity: 1.0 })
    ];

    var overlayUnselectedOpacity = [
      new OpenLayersRule().where('type').is('overlay').use({ strokeOpacity: 0.3 })
    ];
    var borderDefaultOpacity = [
      new OpenLayersRule().where('type').is('underlay').use({ strokeOpacity: 1.0 })
    ];
    var borderUnselectedOpacity = [
      new OpenLayersRule().where('type').is('underlay').use({ strokeOpacity: 0.3 })
    ];

    var roadClassRules = [
      new OpenLayersRule().where('roadClass').is('1').use({ strokeColor: '#ff0000'}),
      new OpenLayersRule().where('roadClass').is('2').use({ strokeColor: '#f60'}),
      new OpenLayersRule().where('roadClass').is('3').use({ strokeColor: '#ff9933'}),
      new OpenLayersRule().where('roadClass').is('4').use({ strokeColor: '#0011bb'}),
      new OpenLayersRule().where('roadClass').is('5').use({ strokeColor: '#33cccc'}),
      new OpenLayersRule().where('roadClass').is('6').use({  strokeColor: '#E01DD9'}),
      new OpenLayersRule().where('roadClass').is('7').use({ strokeColor: '#00ccdd'}),
      new OpenLayersRule().where('roadClass').is('8').use({ strokeColor: '#888'}),
      new OpenLayersRule().where('roadClass').is('9').use({ strokeColor: '#ff55dd'}),
      new OpenLayersRule().where('roadClass').is('10').use({ strokeColor: '#ff55dd'}),
      new OpenLayersRule().where('roadClass').is('11').use({ strokeColor: '#444444'}),
      new OpenLayersRule().where('roadClass').is('99').use({ strokeColor: '#a4a4a2'})
    ];

    var streetSectionRules = [
      // -- TODO
    ];

    var roadPartChangeMarkers = [
      // -- TODO ... or place it somewhere else?
    ];

    // -- Road classification styles

    var typeSpecificStyleLookup = {
      overlay: { strokeOpacity: 1.0 },
      other: { strokeOpacity: 0.7 },
      roadAddressAnomaly: { strokeColor: '#000000', strokeOpacity: 0.6, externalGraphic: 'images/speed-limits/unknown.svg' },
      cutter: { externalGraphic: 'images/cursor-crosshair.svg', pointRadius: 11.5 }
    };

    var roadClassDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      graphicZIndex: "${zIndex}",
      rotation: '${rotation}'}));

    roadClassDefaultStyle.addRules(roadClassRules);
    roadClassDefaultStyle.addRules(unknownRoadAddressAnomalyRules);
    roadClassDefaultStyle.addRules(zoomLevelRules);
    roadClassDefaultStyle.addRules(overlayRules);
    roadClassDefaultStyle.addRules(overlayDefaultOpacity);
    roadClassDefaultStyle.addRules(borderDefaultOpacity);
    roadClassDefaultStyle.addRules(borderRules);
    roadClassDefaultStyle.addRules(FloatingRoadAddressAnomalyRules);
    var roadClassDefaultStyleMap = new OpenLayers.StyleMap({ default: roadClassDefaultStyle });
    roadClassDefaultStyleMap.addUniqueValueRules('default', 'type', typeSpecificStyleLookup);

    var unknownLimitStyleRule = new OpenLayers.Rule({
      filter: typeFilter('unknown'),
      symbolizer: { externalGraphic: 'images/speed-limits/unknown.svg' }
    });
    roadClassDefaultStyle.addRules([unknownLimitStyleRule]);

    var roadClassSelectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.3,
      graphicOpacity: 0.3,
      graphicZIndex: "${zIndex}",
      rotation: '${rotation}'
    }));
    var roadClassSelectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      graphicOpacity: 1.0,
      graphicZIndex: "${zIndex}",
      rotation: '${rotation}'
    }));
    roadClassSelectionDefaultStyle.addRules(roadClassRules);
    roadClassSelectionSelectStyle.addRules(roadClassRules);
    roadClassSelectionDefaultStyle.addRules(unknownRoadAddressAnomalyUnselectedRules);
    roadClassSelectionSelectStyle.addRules(unknownRoadAddressAnomalyRules);
    roadClassSelectionDefaultStyle.addRules(zoomLevelRules);
    roadClassSelectionSelectStyle.addRules(zoomLevelRules);
    roadClassSelectionDefaultStyle.addRules(overlayRules);
    roadClassSelectionSelectStyle.addRules(overlayRules);
    roadClassSelectionDefaultStyle.addRules(linkTypeSizeRules);
    roadClassSelectionSelectStyle.addRules(linkTypeSizeRules);
    roadClassSelectionDefaultStyle.addRules(overlayUnselectedOpacity);
    roadClassSelectionSelectStyle.addRules(overlayDefaultOpacity);
    roadClassSelectionDefaultStyle.addRules(borderRules);
    roadClassSelectionSelectStyle.addRules(borderRules);
    roadClassSelectionDefaultStyle.addRules(borderUnselectedOpacity);
    roadClassSelectionSelectStyle.addRules(borderDefaultOpacity);
    roadClassSelectionDefaultStyle.addRules(FloatingRoadAddressAnomalyUnselectedRules);
    roadClassSelectionSelectStyle.addRules(FloatingRoadAddressAnomalyRules);
    var roadClassSelectionStyleMap = new OpenLayers.StyleMap({
      select: roadClassSelectionSelectStyle,
      default: roadClassSelectionDefaultStyle
    });

    roadClassSelectionStyleMap.addUniqueValueRules('default', 'type', typeSpecificStyleLookup);

    roadClassSelectionDefaultStyle.addRules([unknownLimitStyleRule]);

    var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults());
    var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });

    browseStyleMap.addUniqueValueRules('default', 'type', typeSpecificStyleLookup);

    var selectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.15,
      graphicOpacity: 0.3
    }));
    var selectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      graphicOpacity: 1.0
    }));
    var selectionStyle = new OpenLayers.StyleMap({
      default: selectionDefaultStyle,
      select: selectionSelectStyle
    });
    selectionStyle.addUniqueValueRules('select', 'type', typeSpecificStyleLookup);
    selectionDefaultStyle.addRules([unknownLimitStyleRule]);

    var isUnknown = function(roadLink) {
      return roadLink.anomaly > 0;
    };

    var limitSigns = function(roadLinks) {
      return _.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var road = new OpenLayers.Geometry.LineString(points);
        var signPosition = GeometryUtils.calculateMidpointOfLineString(road);
        var type = isUnknown(roadLink) ? { type: 'unknown' } : {};
        var attributes = _.merge(_.cloneDeep(roadLink), type);
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(signPosition.x, signPosition.y), attributes);
      });
    };

    var renderFeatures = function(addressLinks) {
      var addressLinksWithType = _.map(addressLinks, function(limit) { return _.merge({}, limit, { type: 'other' }); });
      var addressLinksWithAdjustments = _.map(addressLinksWithType, offsetBySideCode);

      return limitSigns(addressLinksWithAdjustments);
    };

    // --- Style map selection
    var getDatasetSpecificStyleMap = function(dataset, renderIntent) {
      var styleMaps = {
        'functional-class': {
          'default': roadClassDefaultStyleMap,
          'select': roadClassSelectionStyleMap
        }
      };
      return styleMaps[dataset][renderIntent];
    };

    var getSpecificStyle = function(renderIntent){
      if(renderIntent === "default"){
        return roadClassDefaultStyle;
      }
      if (renderIntent === "select") {
        return roadClassSelectionDefaultStyle;
      }
    };

    return {
      getDatasetSpecificStyleMap: getDatasetSpecificStyleMap,
      getSpecificStyle: getSpecificStyle,
      renderFeatures: renderFeatures
    };
  };
})(this);
