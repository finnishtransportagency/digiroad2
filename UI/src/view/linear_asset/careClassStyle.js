(function(root) {
  root.CareClassStyle = function() {
    AssetStyle.call(this);
    var me = this;
    var greenCareClass = 'hoitoluokat_viherhoitoluokka';
    var winterCareClass = 'hoitoluokat_talvihoitoluokka';

    var valueExists = function(asset, publicId) {
      return !_.isUndefined(asset.value) && !emptyValues(asset, publicId);
    };

    var findValue = function(asset, publicId) {
      var properties = _.find(asset.value.properties, function(a) { return a.publicId === publicId; });
      if(properties)
          return _.head(properties.values).value;
    };

    var emptyValues = function(asset, publicId) {
      var properties = _.find(asset.value.properties, function(a) { return a.publicId === publicId; });
      return properties ?  !_.isUndefined(asset.id) && _.isEmpty(properties.values): !_.isUndefined(asset.id) ;
    };

    var winterCareClassRules = [
      new StyleRule().where('noWinterCare').is(true).use({ stroke : { color: '#000000'}, icon: {src:  'images/na.svg'}}),
      new StyleRule().where('hasAsset').is(false).use({ stroke : { color: '#7f7f7c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(1).use({stroke: {color: '#880015'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(2).use({stroke: {color: '#f64343'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(3).use({stroke: {color: '#ff982c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(4).use({stroke: {color: '#008000'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(5).use({stroke: {color: '#4ec643'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(6).use({stroke: {color: '#0011bb'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(7).use({stroke: {color: '#00ccdd'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(8).use({stroke: {color: '#a800a8'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(9).use({stroke: {color: '#c559ff'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(10).use({stroke: {color: '#ff55dd'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(11).use({stroke: {color: '#ffe82d'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(20).use({stroke: {color: '#880015'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(30).use({stroke: {color: '#f64343'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(40).use({stroke: {color: '#ff982c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(50).use({stroke: {color: '#0011bb'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(60).use({stroke: {color: '#4ec643'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, winterCareClass)){return findValue(asset, winterCareClass); }}).is(70).use({stroke: {color: '#00ccdd'}})
    ];

    var greenCareClassRules = [
      new StyleRule().where('noGreenCare').is(true).use({ stroke : { color: '#000000'}, icon: {src:  'images/na.svg'}}),
      new StyleRule().where('hasAsset').is(false).use({ stroke : { color: '#7f7f7c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, greenCareClass)){return findValue(asset, greenCareClass); }}).is(1).use({stroke: {color: '#008000'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, greenCareClass)){return findValue(asset, greenCareClass); }}).is(2).use({stroke: {color: '#4ec643'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, greenCareClass)){return findValue(asset, greenCareClass); }}).is(3).use({stroke: {color: '#ffe82d'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, greenCareClass)){return findValue(asset, greenCareClass); }}).is(4).use({stroke: {color: '#0011bb'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, greenCareClass)){return findValue(asset, greenCareClass); }}).is(5).use({stroke: {color: '#00ccdd'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, greenCareClass)){return findValue(asset, greenCareClass); }}).is(6).use({stroke: {color: '#c559ff'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, greenCareClass)){return findValue(asset, greenCareClass); }}).is(7).use({stroke: {color: '#ff55dd'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, greenCareClass)){return findValue(asset, greenCareClass); }}).is(8).use({stroke: {color: '#f64343'}})
    ];

    var careClassSizeRules = [
      new StyleRule().where('zoomLevel').isIn([8 ,9]).use({stroke: {width: 3}, pointRadius: 0}),
      new StyleRule().where('zoomLevel').is(10).use({stroke: {width: 5}, pointRadius: 10}),
      new StyleRule().where('zoomLevel').is(11).use({stroke: {width: 7}, pointRadius: 14}),
      new StyleRule().where('zoomLevel').is(12).use({stroke: {width: 10}, pointRadius: 16}),
      new StyleRule().where('zoomLevel').is(13).use({stroke: {width: 10}, pointRadius: 16}),
      new StyleRule().where('zoomLevel').is(14).use({stroke: {width: 14}, pointRadius: 22}),
      new StyleRule().where('zoomLevel').is(15).use({stroke: {width: 14}, pointRadius: 22})
    ];

    var greenCareClassImageSizeRules = [
      new StyleRule().where('zoomLevel').isIn([8 ,9]).and('noGreenCare').is(true).and('type').isNot('unknown').use({ icon: {scale: 0.8}}),
      new StyleRule().where('zoomLevel').is(10).and('noGreenCare').is(true).and('type').isNot('unknown').use({ icon: {scale: 1}}),
      new StyleRule().where('zoomLevel').is(11).and('noGreenCare').is(true).and('type').isNot('unknown').use({ icon: {scale: 1.3}}),
      new StyleRule().where('zoomLevel').is(12).and('noGreenCare').is(true).and('type').isNot('unknown').use({ icon: {scale: 1.6}}),
      new StyleRule().where('zoomLevel').is(13).and('noGreenCare').is(true).and('type').isNot('unknown').use({ icon: {scale: 1.8}}),
      new StyleRule().where('zoomLevel').is(14).and('noGreenCare').is(true).and('type').isNot('unknown').use({ icon: {scale: 2}}),
      new StyleRule().where('zoomLevel').is(15).and('noGreenCare').is(true).and('type').isNot('unknown').use({ icon: {scale: 2.2}})
    ];

    var winterCareClassImageSizeRules = [
      new StyleRule().where('zoomLevel').isIn([8 ,9]).and('noWinterCare').is(true).and('type').isNot('unknown').use({ icon: {scale: 0.8}}),
      new StyleRule().where('zoomLevel').is(10).and('noWinterCare').is(true).and('type').isNot('unknown').use({ icon: {scale: 1}}),
      new StyleRule().where('zoomLevel').is(11).and('noWinterCare').is(true).and('type').isNot('unknown').use({ icon: {scale: 1.3}}),
      new StyleRule().where('zoomLevel').is(12).and('noWinterCare').is(true).and('type').isNot('unknown').use({ icon: {scale: 1.6}}),
      new StyleRule().where('zoomLevel').is(13).and('noWinterCare').is(true).and('type').isNot('unknown').use({ icon: {scale: 1.8}}),
      new StyleRule().where('zoomLevel').is(14).and('noWinterCare').is(true).and('type').isNot('unknown').use({ icon: {scale: 2}}),
      new StyleRule().where('zoomLevel').is(15).and('noWinterCare').is(true).and('type').isNot('unknown').use({ icon: {scale: 2.2}})
    ];

    var overlayStyleRules = [
      new StyleRule().where('type').is('overlay').and('zoomLevel').isIn([8 ,9]).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 1,  lineDash: [1,6] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(10).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 3,  lineDash: [1,10] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(11).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 5,  lineDash: [1,15] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(12).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 8,  lineDash: [1,22] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(13).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 8,  lineDash: [1,22] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(14).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 12, lineDash: [1,28] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(15).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 12, lineDash: [1,28] }})
    ];

    this.getNewFeatureProperties = function(linearAssets){
      var linearAssetsWithType = _.map(linearAssets, function(linearAsset) {
        var hasAsset = me.hasValue(linearAsset);
        var type =  me.isUnknown(linearAsset) ? { type: 'unknown' } : {type: 'line'};
        return _.merge({}, linearAsset, { hasAsset: hasAsset }, type);
      });

      var offsetBySideCode = function(linearAsset) {
        return GeometryUtils.offsetBySideCode(applicationModel.zoom.level, linearAsset);
      };

      var linearAssetsWithAdjustments = _.map(linearAssetsWithType, offsetBySideCode);
      return _.sortBy(linearAssetsWithAdjustments, function(asset) {
        return asset.expired ? -1 : 1;
      });
    };

    var featureTypeRules = [
      new StyleRule().where('type').is('cutter').use({ icon: {  src: 'images/cursor-crosshair.svg'}})
    ];

    me.browsingStyleProvider = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.browsingStyleProvider.addRules(winterCareClassImageSizeRules);
    me.browsingStyleProvider.addRules(winterCareClassRules);
    me.browsingStyleProvider.addRules(careClassSizeRules);
    me.browsingStyleProvider.addRules(overlayStyleRules);
    me.browsingStyleProvider.addRules(featureTypeRules);

    me.greenCareStyle = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.greenCareStyle.addRules(greenCareClassRules);
    me.greenCareStyle.addRules(careClassSizeRules);
    me.greenCareStyle.addRules(greenCareClassImageSizeRules);
    me.greenCareStyle.addRules(featureTypeRules);
  };
})(this);