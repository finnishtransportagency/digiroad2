(function(root) {
  root.CarryingCapacityStyle = function() {
    AssetStyle.call(this);
    var me = this;

    var valueExists = function(asset) {
      return !_.isUndefined(asset.value);
    };

    var findValue = function(asset, publicId) {
      return _.first(_.find(asset.value.properties, function(a) { return a.publicId === publicId; }).values).value;
    };

    this.renderOverlays = function(linearAssets) {
      return me.lineFeatures(_.map(linearAssets, function(linearAsset) {
        var expired = _.isUndefined(linearAsset.value);
        return _.merge({}, linearAsset, { type: 'overlay' }, { expired: expired }); }));
    };

    me.renderFeatures = function(linearAssets) {
      return  me.lineFeatures(me.getNewFeatureProperties(linearAssets)).concat(me.renderOverlays(linearAssets));
    };

    var springCarryingCapacityRules = [
      new StyleRule().where('expired').is(true).use({ stroke : { color: '#7f7f7c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "kevatkantavuus"); }}).isBetween([0, 162]).use({stroke: {color: '#ac0019'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "kevatkantavuus"); }}).isBetween([162, 287]).use({stroke: {color: '#ff0000'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "kevatkantavuus"); }}).isBetween([287, 434]).use({stroke: {color: '#ff982c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "kevatkantavuus"); }}).isBetween([434, 671]).use({stroke: {color: '#ffe82d'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "kevatkantavuus"); }}).isBetween([671, 2051]).use({stroke: {color: '#11bb00'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "kevatkantavuus"); }}).isGreater(2051).use({stroke: {color: '#439232'}})
    ];

    var frostHeavingFactorRules = [
      new StyleRule().where('expired').is(true).use({ stroke : { color: '#7f7f7c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "routivuuskerroin"); }}).is(40).use({stroke: {color: '#0011bb'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "routivuuskerroin"); }}).is(50).use({stroke: {color: '#11bb00'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "routivuuskerroin"); }}).is(60).use({stroke: {color: '#11bb00'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "routivuuskerroin"); }}).is(70).use({stroke: {color: '#11bb00'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "routivuuskerroin"); }}).is(80).use({stroke: {color: '#11bb00'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return findValue(asset, "routivuuskerroin"); }}).is(999).use({stroke: {color: '#ff0000'}})
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

    // var overlayStyleRules = [
    //   new StyleRule().where('type').is('overlay').and('zoomLevel').is(9).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 1,  lineDash: [1,6] }}),
    //   new StyleRule().where('type').is('overlay').and('zoomLevel').is(10).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 3,  lineDash: [1,10] }}),
    //   new StyleRule().where('type').is('overlay').and('zoomLevel').is(11).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 5,  lineDash: [1,15] }}),
    //   new StyleRule().where('type').is('overlay').and('zoomLevel').is(12).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 8,  lineDash: [1,22] }}),
    //   new StyleRule().where('type').is('overlay').and('zoomLevel').is(13).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 8,  lineDash: [1,22] }}),
    //   new StyleRule().where('type').is('overlay').and('zoomLevel').is(14).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 12, lineDash: [1,28] }}),
    //   new StyleRule().where('type').is('overlay').and('zoomLevel').is(15).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 12, lineDash: [1,28] }})
    // ];

    me.frostHeavingFactorStyle = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.frostHeavingFactorStyle.addRules(frostHeavingFactorRules);
    me.frostHeavingFactorStyle.addRules(carryingCapacityFeatureSizeRules);
    // me.frostHeavingFactorStyle.addRules(overlayStyleRules);

    me.springCarryingCapacityStyle = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.springCarryingCapacityStyle.addRules(springCarryingCapacityRules);
    me.springCarryingCapacityStyle.addRules(carryingCapacityFeatureSizeRules);

  };
})(this);