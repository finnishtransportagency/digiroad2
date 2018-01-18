(function(root) {
  root.LinkPropertyLayerStyles = function(roadLayer) {

    var functionalClassRules = [
      new StyleRule().where('functionalClass').is(1).use({ stroke : { color: '#f00'}, fill : { color: '#f00'}, icon: { src: 'images/link-properties/arrow-drop-red.svg'} }),
      new StyleRule().where('functionalClass').is(2).use({ stroke : { color: '#f00'}, fill : { color: '#f00'}, icon: { src: 'images/link-properties/arrow-drop-red.svg'} }),
      new StyleRule().where('functionalClass').is(3).use({ stroke : { color: '#f5d'}, fill : { color: '#f5d'}, icon: { src: 'images/link-properties/arrow-drop-pink.svg'} }),
      new StyleRule().where('functionalClass').is(4).use({ stroke : { color: '#f5d'}, fill : { color: '#f5d'}, icon: { src: 'images/link-properties/arrow-drop-pink.svg'} }),
      new StyleRule().where('functionalClass').is(5).use({ stroke : { color: '#01b'}, fill : { color: '#01b'}, icon: { src: 'images/link-properties/arrow-drop-blue.svg'} }),
      new StyleRule().where('functionalClass').is(6).use({ stroke : { color: '#01b'}, fill : { color: '#01b'}, icon: { src: 'images/link-properties/arrow-drop-blue.svg'} }),
      new StyleRule().where('functionalClass').is(7).use({ stroke : { color: '#888'}, fill : { color: '#888'}, icon: { src: 'images/link-properties/arrow-drop-grey.svg'} }),
      new StyleRule().where('functionalClass').is(8).use({ stroke : { color: '#888'}, fill : { color: '#888'}, icon: { src: 'images/link-properties/arrow-drop-grey.svg'} })
    ];

    var unknownFunctionalClassDefaultRules = [
      new StyleRule().where('functionalClass').is(99).use({ stroke: { color: '#000', opacity: 0.6}, icon: { src: 'images/link-properties/arrow-drop-black.svg' } })
    ];

    var zoomLevelRules = [
      new StyleRule().where('zoomLevel').is(9).use({ stroke: {width: 3 }, pointRadius: 0 }),
      new StyleRule().where('zoomLevel').is(10).use({ stroke: {width: 5 }, pointRadius: 10 }),
      new StyleRule().where('zoomLevel').is(11).use({ stroke: {width: 8 }, pointRadius: 12 }),
      new StyleRule().where('zoomLevel').is(12).use({ stroke: {width: 10 }, pointRadius: 13 }),
      new StyleRule().where('zoomLevel').is(13).use({ stroke: {width: 10 }, pointRadius: 14 }),
      new StyleRule().where('zoomLevel').is(14).use({ stroke: {width: 14 }, pointRadius: 16 }),
      new StyleRule().where('zoomLevel').is(15).use({ stroke: {width: 14 }, pointRadius: 16 })
    ];

    var overlayRules = [
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(9).use({ stroke: {color: '#fff', lineCap: 'square', width: 1, lineDash: [1, 6] } }),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(10).use({ stroke: {color: '#fff', lineCap: 'square', width: 3, lineDash: [1, 10] } }),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(11).use({ stroke: {color: '#fff', lineCap: 'square', width: 5, lineDash: [1, 15] } }),
      new StyleRule().where('type').is('overlay').and('zoomLevel').isIn([12, 13]).use({ stroke: {color: '#fff', lineCap: 'square', width: 8, lineDash: [1, 22] } }),
      new StyleRule().where('type').is('overlay').and('zoomLevel').isIn([14, 15]).use({ stroke: {color: '#fff', lineCap: 'square', width: 12, lineDash: [1, 28] } })
    ];

    var linkTypeSizeRules = [
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).use({ stroke: { width: 6 } }),
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').is(10).use({ stroke: { width: 2 } }),
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').is(11).use({ stroke: { width: 4 } }),
      new StyleRule().where('type').is('overlay').and('linkType').isIn([8, 9, 12, 21]).use({ stroke: {color: '#fff', lineCap: 'square', width: 4, lineDash: [1, 16] } }),
      new StyleRule().where('type').is('overlay').and('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').is(10).use({ stroke: {color: '#fff', lineCap: 'square', width: 1, lineDash: [1, 8] } }),
      new StyleRule().where('type').is('overlay').and('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').is(11).use({ stroke: {color: '#fff', lineCap: 'square', width: 2, lineDash: [1, 8] } })
    ];

    var overlayDefaultOpacity = [
      new StyleRule().where('type').is('overlay').use({ stroke: { opacity: 1.0 } })
    ];

    var overlayUnselectedOpacity = [
      new StyleRule().where('type').is('overlay').use({ stroke: { opacity: 0.3 } })
    ];

    var administrativeClassRules = [
      new StyleRule().where('administrativeClass').is('Private').use({ stroke: { color: '#01b'}, icon: { src: 'images/link-properties/arrow-drop-blue.svg' } }),
      new StyleRule().where('administrativeClass').is('Municipality').use({ stroke: {color: '#1b0'}, icon: { src: 'images/link-properties/arrow-drop-green.svg' } }),
      new StyleRule().where('administrativeClass').is('State').use({ stroke: { color: '#f00'},  icon: { src: 'images/link-properties/arrow-drop-red.svg' } }),
      new StyleRule().where('administrativeClass').is('Unknown').use({ stroke: { color: '#888'}, icon: { src: 'images/link-properties/arrow-drop-grey.svg' } })
    ];

    var linkStatusRules = [
      new StyleRule().where('constructionType').is(1).use({ stroke: { color: '#ff9900' } }),
      new StyleRule().where('constructionType').is(3).use({ stroke: { color: '#cc99ff'} })
    ];

    var linkTypeRules = [
      new StyleRule().where('linkType').isIn([2, 3]).use({ stroke: { color: '#01b'}, icon: { src: 'images/link-properties/arrow-drop-blue.svg' } }),
      new StyleRule().where('linkType').isIn([1, 4]).use({ stroke: { color: '#f00'}, icon: { src: 'images/link-properties/arrow-drop-red.svg' } }),
      new StyleRule().where('linkType').isIn([5, 6]).use({ stroke: { color: '#0cd'}, icon: { src: 'images/link-properties/arrow-drop-cyan.svg' } }),
      new StyleRule().where('linkType').isIn([8, 9]).use({ stroke: { color: '#888'}, icon: { src: 'images/link-properties/arrow-drop-grey.svg' } }),
      new StyleRule().where('linkType').isIn([7, 10, 11, 12]).use({ stroke: { color: '#1b0'}, icon: { src: 'images/link-properties/arrow-drop-green.svg' } }),
      new StyleRule().where('linkType').isIn([13, 21]).use({ stroke: { color: '#f5d'}, icon: { src: 'images/link-properties/arrow-drop-pink.svg' } })
    ];
    var unknownLinkTypeDefaultRules = [
      new StyleRule().where('linkType').is(99).use({ stroke: { color: '#000', opacity: 0.6}, icon: { src: 'images/link-properties/arrow-drop-black.svg' } })
    ];
    var unknownLinkTypeUnselectedRules = [
      new StyleRule().where('linkType').is(99).use({ stroke: { color: '#000', opacity: 0.3}, icon: { src: 'images/link-properties/arrow-drop-black.svg' } })
    ];

    var verticalLevelRules = [
      new StyleRule().where('verticalLevel').is(-11).use({ stroke: { color: '#01b'}, icon: { src: 'images/link-properties/arrow-drop-blue.svg' } }),
      new StyleRule().where('verticalLevel').is(-1).use({ stroke: { color: '#f00'}, icon: { src: 'images/link-properties/arrow-drop-red.svg' } }),
      new StyleRule().where('verticalLevel').is(0).use({ stroke: { color: '#888'}, icon: { src: 'images/link-properties/arrow-drop-grey.svg' } }),
      new StyleRule().where('verticalLevel').is(1).use({ stroke: { color: '#1b0'}, icon: { src: 'images/link-properties/arrow-drop-green.svg' } }),
      new StyleRule().where('verticalLevel').is(2).use({ stroke: { color: '#f5d'}, icon: { src: 'images/link-properties/arrow-drop-pink.svg' } }),
      new StyleRule().where('verticalLevel').is(3).use({ stroke: { color: '#0cd'}, icon: { src: 'images/link-properties/arrow-drop-cyan.svg' } }),
      new StyleRule().where('verticalLevel').is(4).use({ stroke: { color: '#444'}, icon: { src: 'images/link-properties/arrow-drop-grey.svg' } })
    ];

    var unknownVerticalLevelDefaultRules = [
      new StyleRule().where('verticalLevel').is(99).use({ stroke: { color: '#000', opacity: 0.3}, icon: { src: 'images/link-properties/arrow-drop-black.svg' } })
    ];

    var unknownVerticalLevelUnselectedRules = [
      new StyleRule().where('verticalLevel').is(99).use({ stroke: { color: '#000', opacity: 0.3}, icon: { src: 'images/link-properties/arrow-drop-black.svg' } })
    ];

    var unknownLinkTypeHistoryDefaultRules = [
      new StyleRule().where('linkType').is(99).use({ stroke: { color: '#000', opacity: 0.3}, icon: { src: 'images/link-properties/arrow-drop-black.svg'} })
    ];

    var unknownFunctionalClassHistoryDefaultRules = [
      new StyleRule().where('functionalClass').is(99).use({ stroke: { color: '#000', opacity: 0.3}, icon: { src: 'images/link-properties/arrow-drop-black.svg' } })
    ];

    var zoomLevelHistoryRules = [
      new StyleRule().where('zoomLevel').is(9).use({ stroke: { width: 1 },  pointRadius: 0 }),
      new StyleRule().where('zoomLevel').is(10).use({ stroke: { width: 2 }, pointRadius: 10 }),
      new StyleRule().where('zoomLevel').is(11).use({ stroke: { width: 4 }, pointRadius: 12 }),
      new StyleRule().where('zoomLevel').is(12).use({ stroke: { width: 5 }, pointRadius: 13 }),
      new StyleRule().where('zoomLevel').is(13).use({ stroke: { width: 5 }, pointRadius: 14 }),
      new StyleRule().where('zoomLevel').is(14).use({ stroke: { width: 7 }, pointRadius: 16 }),
      new StyleRule().where('zoomLevel').is(15).use({ stroke: { width: 7 }, pointRadius: 16 })
    ];

    var linkTypeSizeHistoryRules = [
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).use({ stroke: {width: 3 }}),
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').is(10).use({ stroke: { width: 1 }}),
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').is(11).use({ stroke: { width: 2 }}),
      new StyleRule().where('type').is('overlay').and('linkType').isIn([8, 9, 12, 21]).use({ stroke: { color: '#fff', lineCap: 'square', width: 2, lineDash: [1, 16] } }),
      new StyleRule().where('type').is('overlay').and('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').is(10).use({ stroke: { color: '#fff', lineCap: 'square', width: 1, lineDash: [1, 8] } }),
      new StyleRule().where('type').is('overlay').and('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').is(11).use({ stroke: { color: '#fff', lineCap: 'square', width: 2, lineDash: [1, 16] } })
    ];

    var overlayHistoryRules = [
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(9).use({ stroke: { color: '#fff', lineCap: 'square', width: 1, lineDash: [1, 6] } }),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(10).use({ stroke: { color: '#fff', lineCap: 'square', width: 1, lineDash: [1, 10] } }),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(11).use({ stroke: { color: '#fff', lineCap: 'square', width: 2, lineDash: [1, 15] } }),
      new StyleRule().where('type').is('overlay').and('zoomLevel').isIn([12, 13]).use({ stroke: { color: '#fff', lineCap: 'square', width: 4, lineDash: [1, 22] } }),
      new StyleRule().where('type').is('overlay').and('zoomLevel').isIn([14, 15]).use({ stroke: { color: '#fff', lineCap: 'square', width: 5, lineDash: [1, 28] } })
    ];

    var functionalClassDefaultStyleProvider = new StyleRuleProvider({stroke : { color: "#a4a4a2", opacity: 0.7 }, fill : { opacity: 0.7 } });
    functionalClassDefaultStyleProvider.addRules(functionalClassRules);
    functionalClassDefaultStyleProvider.addRules(unknownFunctionalClassDefaultRules);
    functionalClassDefaultStyleProvider.addRules(zoomLevelRules);
    functionalClassDefaultStyleProvider.addRules(overlayRules);
    functionalClassDefaultStyleProvider.addRules(linkTypeSizeRules);
    functionalClassDefaultStyleProvider.addRules(overlayDefaultOpacity);
    functionalClassDefaultStyleProvider.addRules(linkStatusRules);

    var administrativeClassDefaultStyleProvider = new StyleRuleProvider({stroke : { opacity: 0.7 }});
    administrativeClassDefaultStyleProvider.addRules(zoomLevelRules);
    administrativeClassDefaultStyleProvider.addRules(administrativeClassRules);
    administrativeClassDefaultStyleProvider.addRules(linkTypeSizeRules);
    administrativeClassDefaultStyleProvider.addRules(linkStatusRules);

    var linkTypeDefaultStyleProvider = new StyleRuleProvider({stroke : { opacity: 0.7 }});
    linkTypeDefaultStyleProvider.addRules(linkTypeRules);
    linkTypeDefaultStyleProvider.addRules(unknownLinkTypeDefaultRules);
    linkTypeDefaultStyleProvider.addRules(zoomLevelRules);
    linkTypeDefaultStyleProvider.addRules(overlayRules);
    linkTypeDefaultStyleProvider.addRules(linkTypeSizeRules);
    linkTypeDefaultStyleProvider.addRules(overlayDefaultOpacity);
    linkTypeDefaultStyleProvider.addRules(linkStatusRules);

    var verticalLevelDefaultStyleProvider = new StyleRuleProvider({stroke : { opacity: 0.7 }});
    verticalLevelDefaultStyleProvider.addRules(verticalLevelRules);
    verticalLevelDefaultStyleProvider.addRules(unknownVerticalLevelDefaultRules);
    verticalLevelDefaultStyleProvider.addRules(zoomLevelRules);
    verticalLevelDefaultStyleProvider.addRules(overlayRules);
    verticalLevelDefaultStyleProvider.addRules(linkTypeSizeRules);
    verticalLevelDefaultStyleProvider.addRules(overlayDefaultOpacity);
    verticalLevelDefaultStyleProvider.addRules(linkStatusRules);

    var functionalClassHistoryDefaultStyleProvider = new StyleRuleProvider({stroke : { opacity: 0.3 }});
    functionalClassHistoryDefaultStyleProvider.addRules(functionalClassRules);
    functionalClassHistoryDefaultStyleProvider.addRules(unknownFunctionalClassHistoryDefaultRules);
    functionalClassHistoryDefaultStyleProvider.addRules(zoomLevelHistoryRules);
    functionalClassHistoryDefaultStyleProvider.addRules(overlayHistoryRules);
    functionalClassHistoryDefaultStyleProvider.addRules(linkTypeSizeHistoryRules);
    functionalClassHistoryDefaultStyleProvider.addRules(overlayDefaultOpacity);
    functionalClassHistoryDefaultStyleProvider.addRules(linkStatusRules);

    var linkTypeHistoryDefaultStyleProvider = new StyleRuleProvider({stroke : { opacity: 0.3 }});
    linkTypeHistoryDefaultStyleProvider.addRules(linkTypeRules);
    linkTypeHistoryDefaultStyleProvider.addRules(unknownLinkTypeHistoryDefaultRules);
    linkTypeHistoryDefaultStyleProvider.addRules(zoomLevelHistoryRules);
    linkTypeHistoryDefaultStyleProvider.addRules(overlayHistoryRules);
    linkTypeHistoryDefaultStyleProvider.addRules(linkTypeSizeHistoryRules);
    linkTypeHistoryDefaultStyleProvider.addRules(overlayDefaultOpacity);
    linkTypeHistoryDefaultStyleProvider.addRules(linkStatusRules);

    // --- Style map selection
    var getDatasetSpecificStyle = function(dataset, renderIntent) {
      var styleProviders = {
        'functional-class': {
          'default': functionalClassDefaultStyleProvider,
          'select': functionalClassDefaultStyleProvider,
          'history': {
            'default': linkTypeHistoryDefaultStyleProvider,
            'select': linkTypeHistoryDefaultStyleProvider
          }
        },
        'administrative-class': {
          'default': administrativeClassDefaultStyleProvider,
          'select': administrativeClassDefaultStyleProvider
        },
        'link-type': {
          'default': linkTypeDefaultStyleProvider,
          'select': linkTypeDefaultStyleProvider,
          'history': {
            'default': linkTypeHistoryDefaultStyleProvider,
            'select': linkTypeHistoryDefaultStyleProvider
          }
        },
        'vertical-level': {
          'default': verticalLevelDefaultStyleProvider,
          'select': verticalLevelDefaultStyleProvider
        }
      };
      return styleProviders[dataset][renderIntent];
    };

    return {
      getDatasetSpecificStyle: getDatasetSpecificStyle
    };
  };
})(this);
