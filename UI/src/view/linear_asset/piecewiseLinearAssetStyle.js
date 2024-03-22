(function(root) {
  root.PiecewiseLinearAssetStyle = function() {
    AssetStyle.call(this);
    var me = this;

    var expirationRules = [
      new StyleRule().where('hasAsset').is(false).use({ stroke : { color: '#7f7f7c'}}),
      new StyleRule().where('hasAsset').is(true).use({ stroke : { color: '#ff0000'}})
    ];

    var zoomLevelRules = [
      new StyleRule().where('zoomLevel').isIn([8 ,9]).use({ stroke: {width: 3 }}),
      new StyleRule().where('zoomLevel').is(10).use({ stroke: {width: 5 }}),
      new StyleRule().where('zoomLevel').is(11).use({ stroke: {width: 8 }}),
      new StyleRule().where('zoomLevel').is(12).use({ stroke: {width: 10 }}),
      new StyleRule().where('zoomLevel').is(13).use({ stroke: {width: 10 }}),
      new StyleRule().where('zoomLevel').is(14).use({ stroke: {width: 14 }}),
      new StyleRule().where('zoomLevel').is(15).use({ stroke: {width: 14 }})
    ];

    var oneWayRules = [
      new StyleRule().where('sideCode').isIn([2,3]).and('trafficDirection').is('BothDirections').and('zoomLevel').is(9).use({ stroke: {width: 2 }}),
      new StyleRule().where('sideCode').isIn([2,3]).and('trafficDirection').is('BothDirections').and('zoomLevel').is(10).use({ stroke: {width: 4 }}),
      new StyleRule().where('sideCode').isIn([2,3]).and('trafficDirection').is('BothDirections').and('zoomLevel').is(11).use({ stroke: {width: 4 }}),
      new StyleRule().where('sideCode').isIn([2,3]).and('trafficDirection').is('BothDirections').and('zoomLevel').is(12).use({ stroke: {width: 5 }}),
      new StyleRule().where('sideCode').isIn([2,3]).and('trafficDirection').is('BothDirections').and('zoomLevel').is(13).use({ stroke: {width: 5 }}),
      new StyleRule().where('sideCode').isIn([2,3]).and('trafficDirection').is('BothDirections').and('zoomLevel').is(14).use({ stroke: {width: 8 }}),
      new StyleRule().where('sideCode').isIn([2,3]).and('trafficDirection').is('BothDirections').and('zoomLevel').is(15).use({ stroke: {width: 8 }})
    ];

    var featureTypeRules = [
      new StyleRule().where('type').is('cutter').use({ icon: {  src: 'images/cursor-crosshair.svg'}})
    ];
   
    var questionMarkerStyleRules = [
      new StyleRule().where('suggested').is(true).use({icon: {src: 'images/icons/questionMarker.png', scale: 0.7, anchor: [0.45, 1]}})
    ];

    var linkTypeSizeRules = [
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).use({ stroke: { width: 6 } }),
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').isIn([10, 9, 8]).use({ stroke: { width: 2 } }),
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').is(11).use({ stroke: { width: 4 } })
    ];

    var linkStatusRules = [
      new StyleRule().where('constructionType').is(4).use({ stroke: { opacity: 0.3} })
    ];

    me.browsingStyleProvider = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.browsingStyleProvider.addRules(expirationRules);
    me.browsingStyleProvider.addRules(zoomLevelRules);
    me.browsingStyleProvider.addRules(oneWayRules);
    me.browsingStyleProvider.addRules(featureTypeRules);
    me.browsingStyleProvider.addRules(questionMarkerStyleRules);
    me.browsingStyleProvider.addRules(linkTypeSizeRules);
    me.browsingStyleProvider.addRules(linkStatusRules);

    me.browsingStyleProviderReadOnly =  new StyleRuleProvider({ stroke : { opacity: 0.3 , color: '#439232'}});
    me.browsingStyleProviderReadOnly.addRules(zoomLevelRules);
    me.browsingStyleProviderReadOnly.addRules(oneWayRules);
    me.browsingStyleProviderReadOnly.addRules(questionMarkerStyleRules);
    me.browsingStyleProviderReadOnly.addRules(linkTypeSizeRules);
    me.browsingStyleProviderReadOnly.addRules(linkStatusRules);
  };
})(this);

