(function(root) {
  root.ParkingProhibitionStyle = function() {
    AssetStyle.call(this);
    var me = this;

    var valueExists = function (asset, publicId) {
      return !_.isUndefined(asset.value) && !emptyValues(asset, publicId);
    };

    var findValue = function(asset, publicId) {
      var properties = _.find(asset.value.properties, function(a) { return a.publicId === publicId; });
      if(properties)
        return _.first(properties.values).value;
    };

    var emptyValues = function(asset, publicId) {
      var properties = _.find(asset.value.properties, function(a) { return a.publicId === publicId; });
      return properties ?  !_.isUndefined(asset.id) && _.isEmpty(properties.values): !_.isUndefined(asset.id) ;
    };

    me.renderFeatures = function(linearAssets) {
      return me.lineFeatures(me.getNewFeatureProperties(linearAssets));
    };

    var parkingProhibitionStyleRules = [
      new StyleRule().where('hasAsset').is(false).use({ stroke : { color: '#7f7f7c' }}),
      new StyleRule().where(function(asset){if(valueExists(asset, "parking_prohibition")){return findValue(asset, "parking_prohibition"); }}).is(1).use({stroke: {color: '#ff0000'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "parking_prohibition")){return findValue(asset, "parking_prohibition"); }}).is(2).use({stroke: {color: '#00adbb'}})
    ];

    var featureTypeRules = [
      new StyleRule().where('type').is('cutter').use({ icon: { src: 'images/cursor-crosshair.svg' } })
    ];

    var parkingProhibitionSizeRules = [
      new StyleRule().where('zoomLevel').isIn([2,3,4]).use({stroke: {width: 8}}),
      new StyleRule().where('zoomLevel').isIn([5,6,7,8]).use({stroke: {width: 7}}),
      new StyleRule().where('zoomLevel').is(9).use({stroke: {width: 3}}),
      new StyleRule().where('zoomLevel').is(10).use({stroke: {width: 5}}),
      new StyleRule().where('zoomLevel').is(11).use({stroke: {width: 7}}),
      new StyleRule().where('zoomLevel').isIn([12,13]).use({stroke: {width: 10}}),
      new StyleRule().where('zoomLevel').isIn([14,15]).use({stroke: {width: 14}})
    ];

    me.browsingStyleProvider = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.browsingStyleProvider.addRules(parkingProhibitionStyleRules);
    me.browsingStyleProvider.addRules(parkingProhibitionSizeRules);
    me.browsingStyleProvider.addRules(featureTypeRules);

  };
})(this);