(function(root) {
  root.PavedRoadStyle = function() {
    AssetStyle.call(this);
    var me = this;

    var valueExists = function(asset) {
      return !_.isUndefined(asset.value);
    };

    var findValue = function(asset, publicId) {
      return _.find(asset.value, function(a) { return a.publicId === publicId; }).value;
    };

    this.renderOverlays = function(linearAssets) {
      return me.lineFeatures(_.map(linearAssets, function(linearAsset) {
        var expired = _.isUndefined(linearAsset.value);
        return _.merge({}, linearAsset, { type: 'overlay' }, { expired: expired }); }));
    };

    me.renderFeatures = function(linearAssets) {
      return  me.lineFeatures(me.getNewFeatureProperties(linearAssets)).concat(me.renderOverlays(linearAssets));
    };

    var pavedRoadStyleRules = [
      new StyleRule().where('expired').is(true).use({ stroke : { color: '#7f7f7c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "paallysteluokka"); }}).is(1).use({stroke: {color: '#c559ff'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "paallysteluokka"); }}).is(2).use({stroke: {color: '#ff55dd'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "paallysteluokka"); }}).is(10).use({stroke: {color: '#ff0000'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "paallysteluokka"); }}).is(20).use({stroke: {color: '#0011bb'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "paallysteluokka"); }}).is(30).use({stroke: {color: '#11bb00'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "paallysteluokka"); }}).is(40).use({stroke: {color: '#ffe82d'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "paallysteluokka"); }}).is(50).use({stroke: {color: '#a52a2a'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "paallysteluokka"); }}).is(99).use({stroke: {color: '#000000'}})
    ];

    var pavedRoadFeatureSizeRules = [
      new StyleRule().where('zoomLevel').isIn([2,3,4]).use({stroke: {width: 8}}),
      new StyleRule().where('zoomLevel').isIn([5,6,7,8]).use({stroke: {width: 7}}),
      new StyleRule().where('zoomLevel').is(9).use({stroke: {width: 3}}),
      new StyleRule().where('zoomLevel').is(10).use({stroke: {width: 5}}),
      new StyleRule().where('zoomLevel').is(11).use({stroke: {width: 7}}),
      new StyleRule().where('zoomLevel').isIn([12,13]).use({stroke: {width: 10}}),
      new StyleRule().where('zoomLevel').isIn([14,15]).use({stroke: {width: 14}})
    ];

    me.browsingStyleProvider = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.browsingStyleProvider.addRules(pavedRoadStyleRules);
    me.browsingStyleProvider.addRules(pavedRoadFeatureSizeRules);

  };
})(this);