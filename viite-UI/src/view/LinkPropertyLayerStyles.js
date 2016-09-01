(function(root) {
  root.LinkPropertyLayerStyles = function(roadLayer) {
    var unknownRoadClassDefaultRules = [
      new OpenLayersRule().where('roadClass').is('99').use({ strokeColor: '#444', externalGraphic: 'images/link-properties/arrow-drop-grey.svg' })
    ];
    var unknownRoadClassUnselectedRules = [
      new OpenLayersRule().where('roadClass').is('99').use({ strokeColor: '#444', externalGraphic: 'images/link-properties/arrow-drop-grey.svg' })
    ];

    var zoomLevelRules = [
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(9).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[9], { pointRadius: 0 })),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(10).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[10], { pointRadius: 10 })),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(11).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[11], { pointRadius: 12 })),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(12).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[12], { pointRadius: 13 })),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(13).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[13], { pointRadius: 14 })),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(14).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[14], { pointRadius: 16 })),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(15).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[15], { pointRadius: 16 }))
    ];

    var overlayRules = [
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

    var roadClassRules = [
      new OpenLayersRule().where('roadClass').is('1').use({ strokeColor: '#f00', externalGraphic: 'images/link-properties/arrow-drop-red.svg' }),
      new OpenLayersRule().where('roadClass').is('2').use({ strokeColor: '#f60', externalGraphic: 'images/link-properties/arrow-drop-red.svg' }),
      new OpenLayersRule().where('roadClass').is('3').use({ strokeColor: '#f93', externalGraphic: 'images/link-properties/arrow-drop-red.svg' }),
      new OpenLayersRule().where('roadClass').is('4').use({ strokeColor: '#01b', externalGraphic: 'images/link-properties/arrow-drop-blue.svg' }),
      new OpenLayersRule().where('roadClass').is('5').use({ strokeColor: '#3cc', externalGraphic: 'images/link-properties/arrow-drop-cyan.svg' }),
      new OpenLayersRule().where('roadClass').is('6').use({ strokeColor: '#c0f', externalGraphic: 'images/link-properties/arrow-drop-pink.svg' }),
      new OpenLayersRule().where('roadClass').is('7').use({ strokeColor: '#0cd',  externalGraphic: 'images/link-properties/arrow-drop-cyan.svg'  }),
      new OpenLayersRule().where('roadClass').is('8').use({ strokeColor: '#888', externalGraphic: 'images/link-properties/arrow-drop-grey.svg' }),
      new OpenLayersRule().where('roadClass').is('9').use({ strokeColor: '#eff', externalGraphic: 'images/link-properties/arrow-drop-blue.svg' }),
      new OpenLayersRule().where('roadClass').is('10').use({ strokeColor: '#666', externalGraphic: 'images/link-properties/arrow-drop-grey.svg' }),
      new OpenLayersRule().where('roadClass').is('11').use({ strokeColor: '#9fc', externalGraphic: 'images/link-properties/arrow-drop-green.svg' })
    ];

    var streetSectionRules = [
      // -- TODO
    ];

    var roadPartChangeMarkers = [
      // -- TODO ... or place it somewhere else?
    ];

    // -- Road classification styles

    var roadClassDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      rotation: '${rotation}'}));
    roadClassDefaultStyle.addRules(roadClassRules);
    roadClassDefaultStyle.addRules(unknownRoadClassDefaultRules);
    roadClassDefaultStyle.addRules(zoomLevelRules);
    roadClassDefaultStyle.addRules(overlayRules);
    roadClassDefaultStyle.addRules(linkTypeSizeRules);
    roadClassDefaultStyle.addRules(overlayDefaultOpacity);
    var roadClassDefaultStyleMap = new OpenLayers.StyleMap({ default: roadClassDefaultStyle });

    var roadClassSelectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.3,
      graphicOpacity: 0.3,
      rotation: '${rotation}'
    }));
    var roadClassSelectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      graphicOpacity: 1.0,
      rotation: '${rotation}'
    }));
    roadClassSelectionDefaultStyle.addRules(roadClassRules);
    roadClassSelectionSelectStyle.addRules(roadClassRules);
    roadClassSelectionDefaultStyle.addRules(unknownRoadClassUnselectedRules);
    roadClassSelectionSelectStyle.addRules(unknownRoadClassDefaultRules);
    roadClassSelectionDefaultStyle.addRules(zoomLevelRules);
    roadClassSelectionSelectStyle.addRules(zoomLevelRules);
    roadClassSelectionDefaultStyle.addRules(overlayRules);
    roadClassSelectionSelectStyle.addRules(overlayRules);
    roadClassSelectionDefaultStyle.addRules(linkTypeSizeRules);
    roadClassSelectionSelectStyle.addRules(linkTypeSizeRules);
    roadClassSelectionDefaultStyle.addRules(overlayUnselectedOpacity);
    roadClassSelectionSelectStyle.addRules(overlayDefaultOpacity);
    var roadClassSelectionStyleMap = new OpenLayers.StyleMap({
      select: roadClassSelectionSelectStyle,
      default: roadClassSelectionDefaultStyle
    });


    // --- Style map selection
    var getDatasetSpecificStyleMap = function(dataset, renderIntent) {
      var styleMaps = {
        'functional-class': {
          'default': roadClassDefaultStyle,
          'select': roadClassSelectionStyleMap
        }
      };
      return styleMaps[dataset][renderIntent];
    };

    return {
      getDatasetSpecificStyleMap: getDatasetSpecificStyleMap
    };
  };
})(this);
