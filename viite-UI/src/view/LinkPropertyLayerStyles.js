(function(root) {
  root.LinkPropertyLayerStyles = function(roadLayer) {

    var normalRoadAddressUnselectedRules = [
      new OpenLayersRule().where('roadLinkType').is(1).use({graphicZIndex: 0})
    ];
    var normalRoadAddressRules = [
      new OpenLayersRule().where('roadLinkType').is(1).use({graphicZIndex: 0})
    ];

    var complementaryRoadAddressUnselectedRules = [
      new OpenLayersRule().where('roadLinkType').is(3).use({graphicZIndex: 4})
    ];

    var complementaryRoadAddressRules = [
      new OpenLayersRule().where('roadLinkType').is(3).use({graphicZIndex: 4})
    ];

    var unknownRoadAddressAnomalyRules = [
      new OpenLayersRule().where('anomaly').is(1).use({ strokeColor: '#000000', strokeOpacity: 0.8, externalGraphic: 'images/speed-limits/unknown.svg', pointRadius: 14, graphicZIndex: 3})
    ];
    var unknownRoadAddressAnomalyUnselectedRules = [
      new OpenLayersRule().where('anomaly').is(1).use({ strokeColor: '#000000', strokeOpacity: 0.3, externalGraphic: 'images/speed-limits/unknown.svg', pointRadius: 14, graphicZIndex: 3})
    ];

    var gapTransferProcessingRules = [
      new OpenLayersRule().where('gapTransfering').is(true).use({ strokeColor: '#00FF00', strokeOpacity: 0.8, graphicZIndex: 4})
    ];

    var gapTransferProcessingUnselectedRules = [
      new OpenLayersRule().where('gapTransfering').is(true).use({ strokeColor: '#00FF00', strokeOpacity: 0.3, graphicZIndex: 4})
    ];

    var floatingRoadAddressRules = [
      new OpenLayersRule().where('roadLinkType').is(-1).use({ strokeColor: '#F7FE2E', strokeOpacity: 0.9, graphicZIndex: 2 })
    ];

    var floatingRoadAddressUnselectedRules = [
      new OpenLayersRule().where('roadLinkType').is(-1).use({ strokeColor: '#FAFF82', strokeOpacity: 0.6, graphicZIndex: 2})
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

    var darkOverlayStyleRule = _.partial(createZoomAndTypeDependentRule, 'overlay-dark');
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
    ];
    var darkOverlayRules = [
      darkOverlayStyleRule(9, { strokeOpacity: 1.0, strokeColor: '#000000', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 6' }),
      darkOverlayStyleRule(10, { strokeOpacity: 1.0, strokeColor: '#000000', strokeLinecap: 'square', strokeWidth: 3, strokeDashstyle: '1 10' }),
      darkOverlayStyleRule(11, { strokeOpacity: 1.0, strokeColor: '#000000', strokeLinecap: 'square', strokeWidth: 5, strokeDashstyle: '1 15' }),
      darkOverlayStyleRule(12, { strokeOpacity: 1.0, strokeColor: '#000000', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
      darkOverlayStyleRule(13, { strokeOpacity: 1.0, strokeColor: '#000000', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
      darkOverlayStyleRule(14, { strokeOpacity: 1.0, strokeColor: '#000000', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' }),
      darkOverlayStyleRule(15, { strokeOpacity: 1.0, strokeColor: '#000000', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' })
    ];

    var borderRules = [
      borderStyleRule(9, { strokeColor: '#000000', strokeOpacity: 1.0, strokeLinecap: 'round', strokeWidth: 7, graphicZIndex: -1}),
      borderStyleRule(10, { strokeColor: '#000000', strokeOpacity: 1.0, strokeLinecap: 'round', strokeWidth: 10, graphicZIndex: -1}),
      borderStyleRule(11, { strokeColor: '#000000', strokeOpacity: 1.0, strokeLinecap: 'round', strokeWidth: 13, graphicZIndex: -1}),
      borderStyleRule(12, { strokeColor: '#000000', strokeOpacity: 1.0, strokeLinecap: 'round', strokeWidth: 17, graphicZIndex: -1}),
      borderStyleRule(13, { strokeColor: '#000000', strokeOpacity: 1.0, strokeLinecap: 'round', strokeWidth: 17, graphicZIndex: -1}),
      borderStyleRule(14, { strokeColor: '#000000', strokeOpacity: 1.0, strokeLinecap: 'round', strokeWidth: 20, graphicZIndex: -1}),
      borderStyleRule(15, { strokeColor: '#000000', strokeOpacity: 1.0, strokeLinecap: 'round', strokeWidth: 20, graphicZIndex: -1})
    ];

    var linkTypeSizeRules = [
      new OpenLayersRule().where('roadLinkType').is(-1).and('zoomLevel', roadLayer.uiState).isIn([9, 10]).use({ strokeWidth: 15}),
      new OpenLayersRule().where('roadLinkType').is(-1).and('zoomLevel', roadLayer.uiState).isIn([11, 12, 13]).use({ strokeWidth: 25}),
      new OpenLayersRule().where('roadLinkType').is(-1).and('zoomLevel', roadLayer.uiState).isIn([14, 15]).use({ strokeWidth: 50})
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
    var darkOverlayDefaultOpacity = [
      new OpenLayersRule().where('type').is('overlay-dark').use({ strokeOpacity: 1.0 })
    ];

    var darkOverlayUnselectedOpacity = [
      new OpenLayersRule().where('type').is('overlay-dark').use({ strokeOpacity: 0.3 })
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

    var constructionTypeRules = [
      new OpenLayersRule().where('constructionType').is('1').and('roadClass').is('99').use({ strokeColor: '#a4a4a2', graphicZIndex: 0}),
      new OpenLayersRule().where('constructionType').is('1').and('anomaly').is(1).use({ strokeColor: '#ff9900', graphicZIndex: 0})
    ];
    var unknownConstructionTypeRule = [new OpenLayersRule().where('type').is('unknownConstructionType').use({externalGraphic: 'images/speed-limits/unknown.svg', pointRadius: 14, graphicZIndex: 3})];

    var streetSectionRules = [
      // -- TODO
    ];

    var roadPartChangeMarkers = [
      // -- TODO ... or place it somewhere else?
    ];

    // -- Road classification styles


    var roadClassDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      graphicZIndex: 0,
      rotation: '${rotation}'}));

    roadClassDefaultStyle.addRules(roadClassRules);
    roadClassDefaultStyle.addRules(normalRoadAddressRules);
    roadClassDefaultStyle.addRules(unknownRoadAddressAnomalyRules);
    roadClassDefaultStyle.addRules(zoomLevelRules);
    roadClassDefaultStyle.addRules(linkTypeSizeRules);
    roadClassDefaultStyle.addRules(overlayRules);
    roadClassDefaultStyle.addRules(overlayDefaultOpacity);
    roadClassDefaultStyle.addRules(borderDefaultOpacity);
    roadClassDefaultStyle.addRules(borderRules);
    roadClassDefaultStyle.addRules(complementaryRoadAddressRules);
    roadClassDefaultStyle.addRules(floatingRoadAddressRules);
    roadClassDefaultStyle.addRules(constructionTypeRules);
    roadClassDefaultStyle.addRules(darkOverlayRules);
    roadClassDefaultStyle.addRules(unknownConstructionTypeRule);
    roadClassDefaultStyle.addRules(gapTransferProcessingRules);
    var roadClassDefaultStyleMap = new OpenLayers.StyleMap({ default: roadClassDefaultStyle });

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
    roadClassSelectionDefaultStyle.addRules(normalRoadAddressUnselectedRules);
    roadClassSelectionSelectStyle.addRules(normalRoadAddressRules);
    roadClassSelectionDefaultStyle.addRules(unknownRoadAddressAnomalyUnselectedRules);
    roadClassSelectionSelectStyle.addRules(unknownRoadAddressAnomalyRules);
    roadClassSelectionDefaultStyle.addRules(zoomLevelRules);
    roadClassSelectionSelectStyle.addRules(zoomLevelRules);
    roadClassSelectionDefaultStyle.addRules(linkTypeSizeRules);
    roadClassSelectionSelectStyle.addRules(linkTypeSizeRules);
    roadClassSelectionDefaultStyle.addRules(overlayRules);
    roadClassSelectionSelectStyle.addRules(overlayRules);
    roadClassSelectionDefaultStyle.addRules(overlayUnselectedOpacity);
    roadClassSelectionSelectStyle.addRules(overlayDefaultOpacity);
    roadClassSelectionDefaultStyle.addRules(borderRules);
    roadClassSelectionSelectStyle.addRules(borderRules);
    roadClassSelectionDefaultStyle.addRules(borderUnselectedOpacity);
    roadClassSelectionSelectStyle.addRules(borderDefaultOpacity);
    roadClassSelectionDefaultStyle.addRules(complementaryRoadAddressUnselectedRules);
    roadClassSelectionSelectStyle.addRules(complementaryRoadAddressRules);
    roadClassSelectionDefaultStyle.addRules(floatingRoadAddressUnselectedRules);
    roadClassSelectionSelectStyle.addRules(floatingRoadAddressRules);
    roadClassSelectionDefaultStyle.addRules(constructionTypeRules);
    roadClassSelectionSelectStyle.addRules(constructionTypeRules);
    roadClassSelectionDefaultStyle.addRules(unknownConstructionTypeRule);
    roadClassSelectionSelectStyle.addRules(unknownConstructionTypeRule);
    roadClassSelectionDefaultStyle.addRules(darkOverlayRules);
    roadClassSelectionSelectStyle.addRules(darkOverlayRules);
    roadClassSelectionDefaultStyle.addRules(darkOverlayUnselectedOpacity);
    roadClassSelectionSelectStyle.addRules(darkOverlayDefaultOpacity);
    roadClassSelectionDefaultStyle.addRules(gapTransferProcessingUnselectedRules);
    roadClassSelectionSelectStyle.addRules(gapTransferProcessingRules);
    var roadClassSelectionStyleMap = new OpenLayers.StyleMap({
      select: roadClassSelectionSelectStyle,
      default: roadClassSelectionDefaultStyle
    });


    roadClassSelectionDefaultStyle.addRules([unknownLimitStyleRule]);

    var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults());
    var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });


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
