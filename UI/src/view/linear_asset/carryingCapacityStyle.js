(function(root) {
  root.CarryingCapacityStyle = function() {
    AssetStyle.call(this);
    var me = this;

    var valueExists = function(asset) {
      return !_.isUndefined(asset.value);
    };

    var findValue = function(asset, publicId) {
      var properties = _.find(asset.value.properties, function(a) { return a.publicId === publicId; });
      return (_.isEmpty(properties) || _.isUndefined(properties)) ? 'defaultColor' : _.isEmpty(properties.values) ? "defaultColor" : parseInt(_.first(properties.values).value);
    };

    this.renderOverlays = function(linearAssets) {
      return me.lineFeatures(_.map(linearAssets, function(linearAsset) {
        var hasAsset = !_.isUndefined(linearAsset.id);
        return _.merge({}, linearAsset, { hasAsset: hasAsset }); }));
    };

    me.renderFeatures = function(linearAssets) {
      return  me.lineFeatures(me.getNewFeatureProperties(linearAssets)).concat(me.renderOverlays(linearAssets));
    };

    var springCarryingCapacityRules = [
      new StyleRule().where('hasAsset').is(false).use({ stroke : { color: '#7f7f7c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "kevatkantavuus"); }}).is("defaultColor").use({stroke: {color: '#000000'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "kevatkantavuus"); }}).isBetween([0, 162]).use({stroke: {color: '#ac0019'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "kevatkantavuus"); }}).isBetween([162, 287]).use({stroke: {color: '#ff0000'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "kevatkantavuus"); }}).isBetween([287, 434]).use({stroke: {color: '#ff982c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "kevatkantavuus"); }}).isBetween([434, 671]).use({stroke: {color: '#ffe82d'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "kevatkantavuus"); }}).isBetween([671, 2051]).use({stroke: {color: '#11bb00'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "kevatkantavuus"); }}).isGreaterOrEqual(2051).use({stroke: {color: '#439232'}})
    ];

    var frostHeavingFactorRules = [
      new StyleRule().where('hasAsset').is(false).use({ stroke : { color: '#7f7f7c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "routivuuskerroin"); }}).is(40).use({stroke: {color: '#0011bb'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "routivuuskerroin"); }}).is(50).use({stroke: {color: '#00ccdd'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "routivuuskerroin"); }}).is(60).use({stroke: {color: '#c559ff'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "routivuuskerroin"); }}).is(70).use({stroke: {color: '#ff55dd'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "routivuuskerroin"); }}).is(80).use({stroke: {color: '#11bb00'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "routivuuskerroin"); }}).is(999).use({stroke: {color: '#000000'}})
    ];

    var carryingCapacityFeatureSizeRules = [
      new StyleRule().where('zoomLevel').isIn([2,3,4]).use({stroke: {width: 8}}),
      new StyleRule().where('zoomLevel').isIn([5,6,7,8]).use({stroke: {width: 7}}),
      new StyleRule().where('zoomLevel').is(9).use({stroke: {width: 3}}),
      new StyleRule().where('zoomLevel').is(10).use({stroke: {width: 5}}),
      new StyleRule().where('zoomLevel').is(11).use({stroke: {width: 7}}),
      new StyleRule().where('zoomLevel').isIn([12,13]).use({stroke: {width: 10}}),
      new StyleRule().where('zoomLevel').isIn([14,15]).use({stroke: {width: 14}})
    ];

    var featureTypeRules = [
      new StyleRule().where('type').is('cutter').use({ icon: {  src: 'images/cursor-crosshair.svg'}})
    ];

    var linkTypeSizeRules = [
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).use({ stroke: { width: 6 } }),
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').isIn([10, 9, 8]).use({ stroke: { width: 2 } }),
      new StyleRule().where('linkType').isIn([8, 9, 12, 21]).and('zoomLevel').is(11).use({ stroke: { width: 4 } })
    ];

    var linkStatusRules = [
      new StyleRule().where('constructionType').is(4).use({ stroke: { opacity: 0.3} })
    ];

    me.frostHeavingFactorStyle = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.frostHeavingFactorStyle.addRules(frostHeavingFactorRules);
    me.frostHeavingFactorStyle.addRules(carryingCapacityFeatureSizeRules);
    me.frostHeavingFactorStyle.addRules(featureTypeRules);
    me.frostHeavingFactorStyle.addRules(linkTypeSizeRules);
    me.frostHeavingFactorStyle.addRules(linkStatusRules);

    me.springCarryingCapacityStyle = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.springCarryingCapacityStyle.addRules(springCarryingCapacityRules);
    me.springCarryingCapacityStyle.addRules(carryingCapacityFeatureSizeRules);
    me.springCarryingCapacityStyle.addRules(featureTypeRules);
    me.springCarryingCapacityStyle.addRules(linkTypeSizeRules);
    me.springCarryingCapacityStyle.addRules(linkStatusRules);

  };
})(this);