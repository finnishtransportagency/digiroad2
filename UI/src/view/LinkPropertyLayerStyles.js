(function(root) {
  root.LinkPropertyLayerStyles = function(roadLayer) {
    var functionalClassRules = [
      new OpenLayersRule().where('functionalClass').is(1).use({ strokeColor: '#ff0000', externalGraphic: 'images/link-properties/functional-class-1.svg' }),
      new OpenLayersRule().where('functionalClass').is(2).use({ strokeColor: '#ff0000', externalGraphic: 'images/link-properties/functional-class-2.svg' }),
      new OpenLayersRule().where('functionalClass').is(3).use({ strokeColor: '#ff55dd', externalGraphic: 'images/link-properties/functional-class-3.svg' }),
      new OpenLayersRule().where('functionalClass').is(4).use({ strokeColor: '#ff55dd', externalGraphic: 'images/link-properties/functional-class-4.svg' }),
      new OpenLayersRule().where('functionalClass').is(5).use({ strokeColor: '#0011bb', externalGraphic: 'images/link-properties/functional-class-5.svg' }),
      new OpenLayersRule().where('functionalClass').is(6).use({ strokeColor: '#0011bb', externalGraphic: 'images/link-properties/functional-class-6.svg' }),
      new OpenLayersRule().where('functionalClass').is(7).use({ strokeColor: '#a4a4a2', externalGraphic: 'images/link-properties/functional-class-7.svg' }),
      new OpenLayersRule().where('functionalClass').is(8).use({ strokeColor: '#a4a4a2', externalGraphic: 'images/link-properties/functional-class-8.svg' })
    ];

    var zoomLevelRules = [
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(9).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[9], { pointRadius: 0 })),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(10).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[10], { pointRadius: 12 })),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(11).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[11], { pointRadius: 14 })),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(12).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[12], { pointRadius: 16 })),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(13).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[13], { pointRadius: 20 })),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(14).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[14], { pointRadius: 24 })),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(15).use(_.merge({}, RoadLayerSelectionStyle.linkSizeLookup[15], { pointRadius: 24 }))
    ];

    var overlayRules = [
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 6' }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 3, strokeDashstyle: '1 10' }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 7, strokeDashstyle: '1 18' }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(12).use({ strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 14, strokeDashstyle: '1 32' }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(13).use({ strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 14, strokeDashstyle: '1 32' }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(14).use({ strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 14, strokeDashstyle: '1 32' }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(15).use({ strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 14, strokeDashstyle: '1 32' })
    ];

    var linkTypeSizeRules = [
      new OpenLayersRule().where('linkType').isIn([8, 9, 21]).use({ strokeWidth: 6 }),
      new OpenLayersRule().where('linkType').isIn([8, 9, 21]).and('zoomLevel', roadLayer.uiState).is(10).use({ strokeWidth: 4 }),
      new OpenLayersRule().where('type').is('overlay').and('linkType').isIn([8, 9, 21]).use({ strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 4, strokeDashstyle: '1 10' }),
      new OpenLayersRule().where('type').is('overlay').and('linkType').isIn([8, 9, 21]).and('zoomLevel', roadLayer.uiState).is(10).use({ strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 2, strokeDashstyle: '1 8' }),
    ];

    var administrativeClassRules = [
      new OpenLayersRule().where('administrativeClass').is('Private').use({ strokeColor: '#0011bb', externalGraphic: 'images/link-properties/arrow-blue.svg' }),
      new OpenLayersRule().where('administrativeClass').is('Municipality').use({ strokeColor: '#11bb00', externalGraphic: 'images/link-properties/arrow-green.svg' }),
      new OpenLayersRule().where('administrativeClass').is('State').use({ strokeColor: '#ff0000', externalGraphic: 'images/link-properties/arrow-red.svg' }),
      new OpenLayersRule().where('administrativeClass').is('Unknown').use({ strokeColor: '#888', externalGraphic: 'images/link-properties/arrow-grey.svg' })
    ];

    // --- Functional class style maps

    var functionalClassDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      rotation: '${rotation}'}));
    functionalClassDefaultStyle.addRules(functionalClassRules);
    functionalClassDefaultStyle.addRules(zoomLevelRules);
    functionalClassDefaultStyle.addRules(overlayRules);
    functionalClassDefaultStyle.addRules(linkTypeSizeRules);
    var functionalClassDefaultStyleMap = new OpenLayers.StyleMap({ default: functionalClassDefaultStyle });

    var functionalClassSelectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.3,
      graphicOpacity: 0.3,
      rotation: '${rotation}'
    }));
    var functionalClassSelectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      graphicOpacity: 1.0,
      rotation: '${rotation}'
    }));
    functionalClassSelectionDefaultStyle.addRules(functionalClassRules);
    functionalClassSelectionSelectStyle.addRules(functionalClassRules);
    functionalClassSelectionDefaultStyle.addRules(zoomLevelRules);
    functionalClassSelectionSelectStyle.addRules(zoomLevelRules);
    functionalClassSelectionDefaultStyle.addRules(overlayRules);
    functionalClassSelectionSelectStyle.addRules(overlayRules);
    var functionalClassSelectionStyleMap = new OpenLayers.StyleMap({
      select: functionalClassSelectionSelectStyle,
      default: functionalClassSelectionDefaultStyle
    });

    // --- Administrative class style maps ---

    var administrativeClassDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      rotation: '${rotation}'
    }));
    administrativeClassDefaultStyle.addRules(zoomLevelRules);
    administrativeClassDefaultStyle.addRules(administrativeClassRules);
    var administrativeClassDefaultStyleMap = new OpenLayers.StyleMap({ default: administrativeClassDefaultStyle });

    var administrativeClassSelectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.3,
      graphicOpacity: 0.3,
      rotation: '${rotation}'
    }));
    var administrativeClassSelectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      graphicOpacity: 1.0,
      rotation: '${rotation}'
    }));
    administrativeClassSelectionDefaultStyle.addRules(zoomLevelRules);
    administrativeClassSelectionSelectStyle.addRules(zoomLevelRules);
    administrativeClassSelectionDefaultStyle.addRules(administrativeClassRules);
    administrativeClassSelectionSelectStyle.addRules(administrativeClassRules);
    var administrativeClassSelectionStyleMap = new OpenLayers.StyleMap({
      select: administrativeClassSelectionSelectStyle,
      default: administrativeClassSelectionDefaultStyle
    });

    // --- Link type style maps

    var linkTypeRules = [
      new OpenLayersRule().where('linkType').is(1).use({ strokeColor: '#ff0000',  externalGraphic: 'images/link-properties/arrow-red.svg'   }),
      new OpenLayersRule().where('linkType').is(2).use({ strokeColor: '#0011bb',  externalGraphic: 'images/link-properties/arrow-blue.svg'  }),
      new OpenLayersRule().where('linkType').is(3).use({ strokeColor: '#0011bb',  externalGraphic: 'images/link-properties/arrow-blue.svg'  }),
      new OpenLayersRule().where('linkType').is(4).use({ strokeColor: '#ff0000',  externalGraphic: 'images/link-properties/arrow-red.svg'   }),
      new OpenLayersRule().where('linkType').is(5).use({ strokeColor: '#00ccdd',  externalGraphic: 'images/link-properties/arrow-cyan.svg'  }),
      new OpenLayersRule().where('linkType').is(6).use({ strokeColor: '#00ccdd',  externalGraphic: 'images/link-properties/arrow-cyan.svg'  }),
      new OpenLayersRule().where('linkType').is(7).use({ strokeColor: '#11bb00',  externalGraphic: 'images/link-properties/arrow-green.svg' }),
      new OpenLayersRule().where('linkType').is(8).use({ strokeColor: '#888',     externalGraphic: 'images/link-properties/arrow-grey.svg'  }),
      new OpenLayersRule().where('linkType').is(9).use({ strokeColor: '#888',     externalGraphic: 'images/link-properties/arrow-grey.svg'  }),
      new OpenLayersRule().where('linkType').is(10).use({ strokeColor: '#11bb00', externalGraphic: 'images/link-properties/arrow-green.svg' }),
      new OpenLayersRule().where('linkType').is(11).use({ strokeColor: '#11bb00', externalGraphic: 'images/link-properties/arrow-green.svg' }),
      new OpenLayersRule().where('linkType').is(12).use({ strokeColor: '#11bb00', externalGraphic: 'images/link-properties/arrow-green.svg' }),
      new OpenLayersRule().where('linkType').is(13).use({ strokeColor: '#ff55dd', externalGraphic: 'images/link-properties/arrow-pink.svg'  }),
      new OpenLayersRule().where('linkType').is(21).use({ strokeColor: '#ff55dd', externalGraphic: 'images/link-properties/arrow-pink.svg'  })
    ];

    var linkTypeDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      rotation: '${rotation}'}));
    linkTypeDefaultStyle.addRules(linkTypeRules);
    linkTypeDefaultStyle.addRules(zoomLevelRules);
    linkTypeDefaultStyle.addRules(overlayRules);
    var linkTypeDefaultStyleMap = new OpenLayers.StyleMap({ default: linkTypeDefaultStyle });

    var linkTypeSelectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.3,
      graphicOpacity: 0.3,
      rotation: '${rotation}'
    }));
    var linkTypeSelectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      graphicOpacity: 1.0,
      rotation: '${rotation}'
    }));
    linkTypeSelectionDefaultStyle.addRules(linkTypeRules);
    linkTypeSelectionSelectStyle.addRules(linkTypeRules);
    linkTypeSelectionDefaultStyle.addRules(zoomLevelRules);
    linkTypeSelectionSelectStyle.addRules(zoomLevelRules);
    linkTypeSelectionDefaultStyle.addRules(overlayRules);
    linkTypeSelectionSelectStyle.addRules(overlayRules);
    var linkTypeSelectionStyleMap = new OpenLayers.StyleMap({
      select: linkTypeSelectionSelectStyle,
      default: linkTypeSelectionDefaultStyle
    });

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

    return {
      getDatasetSpecificStyleMap: getDatasetSpecificStyleMap
    };
  };
})(this);
