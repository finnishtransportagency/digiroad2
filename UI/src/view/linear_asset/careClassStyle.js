(function(root) {
  root.CareClassStyle = function() {
    AssetStyle.call(this);
    var me = this;

    var overlayAssets = [10, 20, 30, 40, 50, 60];

    var valueExists = function(asset, publicId) {
      return !_.isUndefined(asset.value) && !emptyValues(asset, publicId);
    };

    var findValue = function(asset, publicId) {
      return _.first(_.find(asset.value.properties, function(a) { return a.publicId === publicId; }).values).value;
    };

    var emptyValues = function(asset, publicId) {
      return _.isEmpty(_.find(asset.value.properties, function(a) { return a.publicId === publicId; }).values);
    };


    me.renderFeatures = function(linearAssets) {
      var groupDottedLines = groupDottedAndSolid(linearAssets);
      var dottedLines = groupDottedLines[true];

      return  me.lineFeatures(me.getNewFeatureProperties(linearAssets)).concat(me.renderOverlays(dottedLines))/*.concat(me.renderExpired(solidLines))*/;
    };

    var groupDottedAndSolid = function(linearAssets) {
      return _.groupBy(linearAssets, function(asset) {
        if(asset.value && !emptyValues(asset, "hoitoluokat_talvihoitoluokka"))
          return _.contains(overlayAssets, parseInt(findValue(asset, "hoitoluokat_talvihoitoluokka")));});
    };

    this.renderOverlays = function(linearAssets) {
      var solidLines = me.lineFeatures(linearAssets);
      var dottedOverlay = me.lineFeatures(_.map(linearAssets, function(asset) { return _.merge({}, asset, { type: 'overlay' }); }));
      return solidLines.concat(dottedOverlay);
    };

    var winterCareClassRules = [
      new StyleRule().where('expired').is(true).use({ stroke : { color: '#7f7f7c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(0).use({stroke: {color: '#880015'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(1).use({stroke: {color: '#f64343'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(2).use({stroke: {color: '#ff982c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(3).use({stroke: {color: '#008000'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(4).use({stroke: {color: '#4ec643'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(5).use({stroke: {color: '#0011bb'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(6).use({stroke: {color: '#00ccdd'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(7).use({stroke: {color: '#c559ff'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(8).use({stroke: {color: '#ff55dd'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(9).use({stroke: {color: '#000000'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(10).use({stroke: {color: '#880015'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(20).use({stroke: {color: '#f64343'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(30).use({stroke: {color: '#ff982c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(40).use({stroke: {color: '#0011bb'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(50).use({stroke: {color: '#4ec643'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_talvihoitoluokka")){return findValue(asset, "hoitoluokat_talvihoitoluokka"); }}).is(60).use({stroke: {color: '#00ccdd'}})
    ];

    var greenCareClassRules = [
      new StyleRule().where('expired').is(true).use({ stroke : { color: '#7f7f7c'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_viherhoitoluokka")){return findValue(asset, "hoitoluokat_viherhoitoluokka"); }}).is(1).use({stroke: {color: '#008000'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_viherhoitoluokka")){return findValue(asset, "hoitoluokat_viherhoitoluokka"); }}).is(2).use({stroke: {color: '#4ec643'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_viherhoitoluokka")){return findValue(asset, "hoitoluokat_viherhoitoluokka"); }}).is(3).use({stroke: {color: '#ffe82d'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_viherhoitoluokka")){return findValue(asset, "hoitoluokat_viherhoitoluokka"); }}).is(4).use({stroke: {color: '#0011bb'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_viherhoitoluokka")){return findValue(asset, "hoitoluokat_viherhoitoluokka"); }}).is(5).use({stroke: {color: '#00ccdd'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_viherhoitoluokka")){return findValue(asset, "hoitoluokat_viherhoitoluokka"); }}).is(6).use({stroke: {color: '#c559ff'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_viherhoitoluokka")){return findValue(asset, "hoitoluokat_viherhoitoluokka"); }}).is(7).use({stroke: {color: '#ff55dd'}}),
      new StyleRule().where(function(asset){if(valueExists(asset, "hoitoluokat_viherhoitoluokka")){return findValue(asset, "hoitoluokat_viherhoitoluokka"); }}).is(8).use({stroke: {color: '#f64343'}})
    ];

    var careClassSizeRules = [
      new StyleRule().where('zoomLevel').is(9).use({stroke: {width: 3}, pointRadius: 0}),
      new StyleRule().where('zoomLevel').is(10).use({stroke: {width: 5}, pointRadius: 10}),
      new StyleRule().where('zoomLevel').is(11).use({stroke: {width: 7}, pointRadius: 14}),
      new StyleRule().where('zoomLevel').is(12).use({stroke: {width: 10}, pointRadius: 16}),
      new StyleRule().where('zoomLevel').is(13).use({stroke: {width: 10}, pointRadius: 16}),
      new StyleRule().where('zoomLevel').is(14).use({stroke: {width: 14}, pointRadius: 22}),
      new StyleRule().where('zoomLevel').is(15).use({stroke: {width: 14}, pointRadius: 22})
    ];

    var overlayStyleRules = [
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(9).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 1,  lineDash: [1,6] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(10).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 3,  lineDash: [1,10] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(11).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 5,  lineDash: [1,15] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(12).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 8,  lineDash: [1,22] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(13).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 8,  lineDash: [1,22] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(14).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 12, lineDash: [1,28] }}),
      new StyleRule().where('type').is('overlay').and('zoomLevel').is(15).and('expired').is(false).use({ stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 12, lineDash: [1,28] }})
    ];


    me.greenCareStyle = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.greenCareStyle.addRules(greenCareClassRules);
    me.greenCareStyle.addRules(careClassSizeRules);

    me.browsingStyleProvider = new StyleRuleProvider({ stroke : { opacity: 0.7 }});
    me.browsingStyleProvider.addRules(winterCareClassRules);
    me.browsingStyleProvider.addRules(careClassSizeRules);
    me.browsingStyleProvider.addRules(overlayStyleRules);

  };
})(this);