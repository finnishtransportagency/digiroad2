(function(root) {
  root.LinkPropertyLayerStyles = function(roadLayer) {
    var functionalClassRules = [
      new OpenLayersRule().where('functionalClass').is(1).use({ strokeColor: '#f00', externalGraphic: 'images/link-properties/arrow-drop-red.svg' }),
      new OpenLayersRule().where('functionalClass').is(2).use({ strokeColor: '#f00', externalGraphic: 'images/link-properties/arrow-drop-red.svg' }),
      new OpenLayersRule().where('functionalClass').is(3).use({ strokeColor: '#f5d', externalGraphic: 'images/link-properties/arrow-drop-pink.svg' }),
      new OpenLayersRule().where('functionalClass').is(4).use({ strokeColor: '#f5d', externalGraphic: 'images/link-properties/arrow-drop-pink.svg' }),
      new OpenLayersRule().where('functionalClass').is(5).use({ strokeColor: '#01b', externalGraphic: 'images/link-properties/arrow-drop-blue.svg' }),
      new OpenLayersRule().where('functionalClass').is(6).use({ strokeColor: '#01b', externalGraphic: 'images/link-properties/arrow-drop-blue.svg' }),
      new OpenLayersRule().where('functionalClass').is(7).use({ strokeColor: '#888', externalGraphic: 'images/link-properties/arrow-drop-grey.svg' }),
      new OpenLayersRule().where('functionalClass').is(8).use({ strokeColor: '#888', externalGraphic: 'images/link-properties/arrow-drop-grey.svg' }),
    ];
    var unknownFunctionalClassDefaultRules = [
      new OpenLayersRule().where('functionalClass').is(99).use({ strokeColor: '#000', strokeOpacity: 0.6, externalGraphic: 'images/link-properties/arrow-drop-black.svg' })
    ];
    var unknownFunctionalClassUnselectedRules = [
      new OpenLayersRule().where('functionalClass').is(99).use({ strokeColor: '#000', strokeOpacity: 0.3, externalGraphic: 'images/link-properties/arrow-drop-black.svg' })
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
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 6' }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 3, strokeDashstyle: '1 10' }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 5, strokeDashstyle: '1 15' }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).isIn([12, 13]).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).isIn([14, 15]).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' })
    ];

    var linkTypeSizeRules = [
      new OpenLayersRule().where('linkType').isIn([8, 9, 12, 21]).use({ strokeWidth: 6 }),
      new OpenLayersRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel', roadLayer.uiState).is(10).use({ strokeWidth: 2 }),
      new OpenLayersRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel', roadLayer.uiState).is(11).use({ strokeWidth: 4 }),
      new OpenLayersRule().where('type').is('overlay').and('linkType').isIn([8, 9, 12, 21]).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 4, strokeDashstyle: '1 16' }),
      new OpenLayersRule().where('type').is('overlay').and('linkType').isIn([8, 9, 12, 21]).and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 8' }),
      new OpenLayersRule().where('type').is('overlay').and('linkType').isIn([8, 9, 12, 21]).and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#fff', strokeLinecap: 'square', strokeWidth: 2, strokeDashstyle: '1 8' })
    ];

    var overlayDefaultOpacity = [
      new OpenLayersRule().where('type').is('overlay').use({ strokeOpacity: 1.0 })
    ];

    var overlayUnselectedOpacity = [
      new OpenLayersRule().where('type').is('overlay').use({ strokeOpacity: 0.3 })
    ];

    var administrativeClassRules = [
      new OpenLayersRule().where('administrativeClass').is('Private').use({ strokeColor: '#01b', externalGraphic: 'images/link-properties/arrow-drop-blue.svg' }),
      new OpenLayersRule().where('administrativeClass').is('Municipality').use({ strokeColor: '#1b0', externalGraphic: 'images/link-properties/arrow-drop-green.svg' }),
      new OpenLayersRule().where('administrativeClass').is('State').use({ strokeColor: '#f00', externalGraphic: 'images/link-properties/arrow-drop-red.svg' }),
      new OpenLayersRule().where('administrativeClass').is('Unknown').use({ strokeColor: '#888', externalGraphic: 'images/link-properties/arrow-drop-grey.svg' })
    ];

    // --- Functional class style maps

    var functionalClassDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      rotation: '${rotation}'}));
    functionalClassDefaultStyle.addRules(functionalClassRules);
    functionalClassDefaultStyle.addRules(unknownFunctionalClassDefaultRules);
    functionalClassDefaultStyle.addRules(zoomLevelRules);
    functionalClassDefaultStyle.addRules(overlayRules);
    functionalClassDefaultStyle.addRules(linkTypeSizeRules);
    functionalClassDefaultStyle.addRules(overlayDefaultOpacity);
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
    functionalClassSelectionDefaultStyle.addRules(unknownFunctionalClassUnselectedRules);
    functionalClassSelectionSelectStyle.addRules(unknownFunctionalClassDefaultRules);
    functionalClassSelectionDefaultStyle.addRules(zoomLevelRules);
    functionalClassSelectionSelectStyle.addRules(zoomLevelRules);
    functionalClassSelectionDefaultStyle.addRules(overlayRules);
    functionalClassSelectionSelectStyle.addRules(overlayRules);
    functionalClassSelectionDefaultStyle.addRules(linkTypeSizeRules);
    functionalClassSelectionSelectStyle.addRules(linkTypeSizeRules);
    functionalClassSelectionDefaultStyle.addRules(overlayUnselectedOpacity);
    functionalClassSelectionSelectStyle.addRules(overlayDefaultOpacity);
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
    administrativeClassDefaultStyle.addRules(linkTypeSizeRules);
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
    administrativeClassSelectionDefaultStyle.addRules(linkTypeSizeRules);
    administrativeClassSelectionSelectStyle.addRules(linkTypeSizeRules);
    var administrativeClassSelectionStyleMap = new OpenLayers.StyleMap({
      select: administrativeClassSelectionSelectStyle,
      default: administrativeClassSelectionDefaultStyle
    });

    // --- Link type style maps

    var linkTypeRules = [
      new OpenLayersRule().where('linkType').isIn([2, 3]).use({ strokeColor: '#01b',  externalGraphic: 'images/link-properties/arrow-drop-blue.svg'  }),
      new OpenLayersRule().where('linkType').isIn([1, 4]).use({ strokeColor: '#f00',  externalGraphic: 'images/link-properties/arrow-drop-red.svg'   }),
      new OpenLayersRule().where('linkType').isIn([5, 6]).use({ strokeColor: '#0cd',  externalGraphic: 'images/link-properties/arrow-drop-cyan.svg'  }),
      new OpenLayersRule().where('linkType').isIn([8, 9]).use({ strokeColor: '#888', externalGraphic: 'images/link-properties/arrow-drop-grey.svg'  }),
      new OpenLayersRule().where('linkType').isIn([7, 10, 11, 12]).use({ strokeColor: '#1b0', externalGraphic: 'images/link-properties/arrow-drop-green.svg' }),
      new OpenLayersRule().where('linkType').isIn([13, 21]).use({ strokeColor: '#f5d', externalGraphic: 'images/link-properties/arrow-drop-pink.svg'  }),
    ];
    var unknownLinkTypeDefaultRules = [
      new OpenLayersRule().where('linkType').is(99).use({ strokeColor: '#000', strokeOpacity: 0.6, externalGraphic: 'images/link-properties/arrow-drop-black.svg' })
    ];
    var unknownLinkTypeUnselectedRules = [
      new OpenLayersRule().where('linkType').is(99).use({ strokeColor: '#000', strokeOpacity: 0.3, externalGraphic: 'images/link-properties/arrow-drop-black.svg' })
    ];

    var linkTypeDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      rotation: '${rotation}'}));
    linkTypeDefaultStyle.addRules(linkTypeRules);
    linkTypeDefaultStyle.addRules(unknownLinkTypeDefaultRules);
    linkTypeDefaultStyle.addRules(zoomLevelRules);
    linkTypeDefaultStyle.addRules(overlayRules);
    linkTypeDefaultStyle.addRules(linkTypeSizeRules);
    linkTypeDefaultStyle.addRules(overlayDefaultOpacity);
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
    linkTypeSelectionDefaultStyle.addRules(unknownLinkTypeUnselectedRules);
    linkTypeSelectionSelectStyle.addRules(unknownLinkTypeDefaultRules);
    linkTypeSelectionDefaultStyle.addRules(zoomLevelRules);
    linkTypeSelectionSelectStyle.addRules(zoomLevelRules);
    linkTypeSelectionDefaultStyle.addRules(overlayRules);
    linkTypeSelectionSelectStyle.addRules(overlayRules);
    linkTypeSelectionDefaultStyle.addRules(linkTypeSizeRules);
    linkTypeSelectionSelectStyle.addRules(linkTypeSizeRules);
    linkTypeSelectionSelectStyle.addRules(overlayUnselectedOpacity);
    linkTypeSelectionSelectStyle.addRules(overlayDefaultOpacity);
    var linkTypeSelectionStyleMap = new OpenLayers.StyleMap({
      select: linkTypeSelectionSelectStyle,
      default: linkTypeSelectionDefaultStyle
    });

    // --- Vertical level style maps

    var verticalLevelRules = [
      //20:  { strokeColor: '#00ccdd', externalGraphic: 'images/speed-limits/20.svg' },
      //30:  { strokeColor: '#ff55dd', externalGraphic: 'images/speed-limits/30.svg' },
      //40:  { strokeColor: '#11bb00', externalGraphic: 'images/speed-limits/40.svg' },
      //50:  { strokeColor: '#ff0000', externalGraphic: 'images/speed-limits/50.svg' },
      //60:  { strokeColor: '#0011bb', externalGraphic: 'images/speed-limits/60.svg' },
      //70:  { strokeColor: '#00ccdd', externalGraphic: 'images/speed-limits/70.svg' },
      //80:  { strokeColor: '#ff0000', externalGraphic: 'images/speed-limits/80.svg' },
      //90:  { strokeColor: '#ff55dd', externalGraphic: 'images/speed-limits/90.svg' },
      //100: { strokeColor: '#11bb00', externalGraphic: 'images/speed-limits/100.svg' },
      //120: { strokeColor: '#0011bb', externalGraphic: 'images/speed-limits/120.svg' }

      new OpenLayersRule().where('verticalLevel').is(-11).use({ strokeColor: '#0cd', externalGraphic: 'images/link-properties/arrow-drop-cyan.svg' }),
      new OpenLayersRule().where('verticalLevel').is(-1).use({ strokeColor: '#f5d', externalGraphic: 'images/link-properties/arrow-drop-pink.svg' }),
      new OpenLayersRule().where('verticalLevel').is(0).use({ strokeColor: '#888', externalGraphic: 'images/link-properties/arrow-drop-grey.svg' }),
      new OpenLayersRule().where('verticalLevel').is(1).use({ strokeColor: '#1b0', externalGraphic: 'images/link-properties/arrow-drop-green.svg' }),
      new OpenLayersRule().where('verticalLevel').is(2).use({ strokeColor: '#f00', externalGraphic: 'images/link-properties/arrow-drop-red.svg' }),
      new OpenLayersRule().where('verticalLevel').is(3).use({ strokeColor: '#01b', externalGraphic: 'images/link-properties/arrow-drop-blue.svg' }),
      new OpenLayersRule().where('verticalLevel').is(4).use({ strokeColor: '#444', externalGraphic: 'images/link-properties/arrow-drop-grey.svg' })
    ];
    var unknownVerticalLevelDefaultRules = [
      new OpenLayersRule().where('verticalLevel').is(99).use({ strokeColor: '#000', strokeOpacity: 0.3, externalGraphic: 'images/link-properties/arrow-drop-black.svg' })
    ];
    var unknownVerticalLevelUnselectedRules = [
      new OpenLayersRule().where('verticalLevel').is(99).use({ strokeColor: '#000', strokeOpacity: 0.3, externalGraphic: 'images/link-properties/arrow-drop-black.svg' })
    ];

    // Vertical level default style map
    var verticalLevelDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      rotation: '${rotation}',
      graphicZIndex: '${verticalLevel}'}));
    verticalLevelDefaultStyle.addRules(verticalLevelRules);
    verticalLevelDefaultStyle.addRules(unknownVerticalLevelDefaultRules);
    verticalLevelDefaultStyle.addRules(zoomLevelRules);
    verticalLevelDefaultStyle.addRules(overlayRules);
    verticalLevelDefaultStyle.addRules(linkTypeSizeRules);
    verticalLevelDefaultStyle.addRules(overlayDefaultOpacity);
    var verticalLevelDefaultStyleMap = new OpenLayers.StyleMap({ default: verticalLevelDefaultStyle });

    // Vertical level selection style map
    var verticalLevelSelectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.3,
      graphicOpacity: 0.3,
      rotation: '${rotation}',
      graphicZIndex: '${verticalLevel}'
    }));
    var verticalLevelSelectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      graphicOpacity: 1.0,
      rotation: '${rotation}',
      graphicZIndex: '${verticalLevel}'
    }));
    verticalLevelSelectionDefaultStyle.addRules(verticalLevelRules);
    verticalLevelSelectionSelectStyle.addRules(verticalLevelRules);
    verticalLevelSelectionDefaultStyle.addRules(unknownVerticalLevelUnselectedRules);
    verticalLevelSelectionSelectStyle.addRules(unknownVerticalLevelDefaultRules);
    verticalLevelSelectionDefaultStyle.addRules(zoomLevelRules);
    verticalLevelSelectionSelectStyle.addRules(zoomLevelRules);
    verticalLevelSelectionDefaultStyle.addRules(overlayRules);
    verticalLevelSelectionSelectStyle.addRules(overlayRules);
    verticalLevelSelectionDefaultStyle.addRules(linkTypeSizeRules);
    verticalLevelSelectionSelectStyle.addRules(linkTypeSizeRules);
    verticalLevelSelectionSelectStyle.addRules(overlayUnselectedOpacity);
    verticalLevelSelectionSelectStyle.addRules(overlayDefaultOpacity);
    var verticalLevelSelectionStyleMap = new OpenLayers.StyleMap({
      select: verticalLevelSelectionSelectStyle,
      default: verticalLevelSelectionDefaultStyle
    });

    // --- Style map selection
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
        },
        'vertical-level': {
          'default': verticalLevelDefaultStyleMap,
          'select': verticalLevelSelectionStyleMap
        }
      };
      return styleMaps[dataset][renderIntent];
    };

    return {
      getDatasetSpecificStyleMap: getDatasetSpecificStyleMap
    };
  };
})(this);
