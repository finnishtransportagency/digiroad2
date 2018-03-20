(function(root) {
  root.ServiceRoadStyle = function() {
    AssetStyle.call(this);
    var me = this;

    var valueExists = function(asset) {
      return !_.isUndefined(asset.value);
    };

    var serviceRoadStyleRules = [

      new StyleRule().where('expired').is(true).use({ stroke : { color: '#7f7f7c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return asset.value[0].value;}}).is(1).use({stroke: {color: '#0011bb'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return asset.value[0].value;}}).is(2).use({stroke: {color: '#11bb00'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return asset.value[0].value;}}).is(3).use({stroke: {color: '#ff69b4'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return asset.value[0].value;}}).is(4).use({stroke: {color: '#00ccdd'}}),
      new StyleRule().where(function(asset){if(valueExists(asset)){return asset.value[0].value;}}).is(99).use({stroke: {color: '#ff0000'}})
    ];

    var serviceRoadFeatureSizeRules = [
      new StyleRule().where('zoomLevel').is(9).use({stroke: {width: 3}, pointRadius: 0}),
      new StyleRule().where('zoomLevel').is(10).use({stroke: {width: 5}, pointRadius: 10}),
      new StyleRule().where('zoomLevel').is(11).use({stroke: {width: 7}, pointRadius: 14}),
      new StyleRule().where('zoomLevel').is(12).use({stroke: {width: 10}, pointRadius: 16}),
      new StyleRule().where('zoomLevel').is(13).use({stroke: {width: 10}, pointRadius: 16}),
      new StyleRule().where('zoomLevel').is(14).use({stroke: {width: 14}, pointRadius: 22}),
      new StyleRule().where('zoomLevel').is(15).use({stroke: {width: 14}, pointRadius: 22})
    ];

    me.browsingStyleProvider = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.browsingStyleProvider.addRules(serviceRoadStyleRules);
    me.browsingStyleProvider.addRules(serviceRoadFeatureSizeRules);
  };
})(this);