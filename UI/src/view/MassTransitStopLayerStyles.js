(function(root) {
  root.MassTransitStopLayerStyles = function(roadLayer) {
    //TODO remove the roadlayer from here because we don't need it here
    var administrativeClassRules = [
      new StyleRule().where('administrativeClass').is('Private').use({ stroke: { color: '#01b' }, icon: { src: 'images/link-properties/arrow-drop-blue.svg' } }),
      new StyleRule().where('administrativeClass').is('Municipality').use({ stroke: { color: '#1b0' }, icon: { src: 'images/link-properties/arrow-drop-green.svg' } }),
      new StyleRule().where('administrativeClass').is('State').use({ stroke: { color: '#f00' }, icon: { src: 'images/link-properties/arrow-drop-red.svg' } }),
      new StyleRule().where('administrativeClass').is('Unknown').use({ stroke: { color: '#888' }, icon: { src: 'images/link-properties/arrow-drop-grey.svg' } })
    ];

    var zoomLevelRules = [
      new StyleRule().where('zoomLevel').is(9).use({ stroke: {  width: 3 },  pointRadius: 0 }),
      new StyleRule().where('zoomLevel').is(10).use({ stroke: {  width: 5 }, pointRadius: 10 }),
      new StyleRule().where('zoomLevel').is(11).use({ stroke: {  width: 8 }, pointRadius: 12 }),
      new StyleRule().where('zoomLevel').is(12).use({ stroke: {  width: 10 }, pointRadius: 13 }),
      new StyleRule().where('zoomLevel').is(13).use({ stroke: {  width: 10 }, pointRadius: 14 }),
      new StyleRule().where('zoomLevel').is(14).use({ stroke: {  width: 14 }, pointRadius: 16 }),
      new StyleRule().where('zoomLevel').is(15).use({ stroke: {  width: 14 },  pointRadius: 16 })
    ];

    var linkStatusRules = [
      new StyleRule().where('constructionType').is(1).use({ stroke: { color: '#ff9900' } }),
      new StyleRule().where('constructionType').is(3).use({ stroke: { color: '#cc99ff'} })
    ];

    var featureTypeRules = [
      new StyleRule().where('type').is('pointer').use({ icon: {  src: 'images/cursor-crosshair.svg'}})
    ];


    var administrativeClassDefaultStyleProvider = new StyleRuleProvider({stroke : { color: "#a4a4a2", opacity: 0.7, width: 5 } });

    administrativeClassDefaultStyleProvider.addRules(administrativeClassRules);
    administrativeClassDefaultStyleProvider.addRules(zoomLevelRules);
    administrativeClassDefaultStyleProvider.addRules(linkStatusRules);
    administrativeClassDefaultStyleProvider.addRules(featureTypeRules);

    var administrativeClassSelectStyleProvider = new StyleRuleProvider({stroke : { color: "#5eaedf", opacity: 1, width: 6 } });

    administrativeClassSelectStyleProvider.addRules(zoomLevelRules);

    return {
      default: administrativeClassDefaultStyleProvider,
      select: administrativeClassSelectStyleProvider
    };
  };
})(this);
