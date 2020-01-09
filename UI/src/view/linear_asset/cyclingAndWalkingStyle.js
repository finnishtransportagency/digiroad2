(function(root) {
    root.CyclingAndWalkingStyle = function() {
        AssetStyle.call(this);
        var me = this;

        var createZoomAndTypeDependentRule = function (type, zoomLevel, style) {
            return new StyleRule().where('type').is(type).and('zoomLevel').is(zoomLevel).use(style);
        };

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
            return me.lineFeatures(me.getNewFeatureProperties(linearAssets)).concat(me.dottedLineFeatures(linearAssets));
        };

        function isOverlayValue (linearAsset) {
            var valuesForOverlay= ["4","7","8","12","14","17","19", "20"];

            if (linearAsset !== undefined && linearAsset.value !== undefined) {

                var property = linearAsset.value.properties.find(function (prop) {
                    if (prop.publicId === "cyclingAndWalking_type")
                        return prop;
                });

                if (property !== undefined && !_.isEmpty(property.values) && valuesForOverlay.indexOf(property.values[0].value) >= 0)
                    return true;
            }
            return false;
        }


        this.dottedLineFeatures = function(linearAssets) {
            var solidLines = me.lineFeatures(linearAssets);
            var dottedOverlay = me.lineFeatures( _.map(linearAssets, function(linearAsset) { return isOverlayValue(linearAsset) ?  _.merge({}, linearAsset, { type: 'overlay' }) :  _.merge({}, linearAsset, { type: 'normal' });}));
            return solidLines.concat(dottedOverlay);
        };

        var cyclingAndWalkingStyleRules = [
            new StyleRule().where(function(asset){ return valueExists(asset, "cyclingAndWalking_type"); }).is(false).use({ stroke : { color: '#7f7f7c' }}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(1).use({ stroke: { color: '#000000', fill: '#000000'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(2).use({ stroke: { color: '#0011bb', fill: '#0011bb'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(3).use({ stroke: { color: '#ff0000', fill: '#ff0000'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(4).use({ stroke: { color: '#880015', fill: '#880015'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(5).use({ stroke: { color: '#880015', fill: '#880015'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(6).use({ stroke: { color: '#a800a8', fill: '#a800a8'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(7).use({ stroke: { color: '#a800a8', fill: '#a800a8'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(8).use({ stroke: { color: '#ffe82d', fill: '#ffe82d'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(9).use({ stroke: { color: '#ffe82d', fill: '#ffe82d'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(10).use({ stroke: { color: '#f64343', fill: '#f64343'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(11).use({ stroke: { color: '#ff55dd', fill: '#ff55dd'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(12).use({ stroke: { color: '#ff55dd', fill: '#ff55dd'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(13).use({ stroke: { color: '#4ec643', fill: '#4ec643'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(14).use({ stroke: { color: '#4ec643', fill: '#4ec643'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(15).use({ stroke: { color: '#00ccdd', fill: '#00ccdd'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(16).use({ stroke: { color: '#008000', fill: '#008000'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(17).use({ stroke: { color: '#00ccdd', fill: '#00ccdd'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(18).use({ stroke: { color: '#444', fill: '#444'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(19).use({ stroke: { color: '#444', fill: '#444'}}),
            new StyleRule().where(function(asset){if(valueExists(asset, "cyclingAndWalking_type")){return findValue(asset, "cyclingAndWalking_type"); }}).is(20).use({ stroke: { color: '#888', fill: '#888'}})

        ];

        var featureTypeRules = [
            new StyleRule().where('type').is('cutter').use({ icon: { src: 'images/cursor-crosshair.svg' } })
        ];

        var linkStatusRules = [
            new StyleRule().where('constructionType').is(1).use({ stroke: { color: '#ff9900'} }),
            new StyleRule().where('constructionType').is(3).use({ stroke: { color: '#cc99ff'} })
        ];


        var overlayStyleRule = _.partial(createZoomAndTypeDependentRule, 'overlay');
        var overlayStyleRules = [
            overlayStyleRule(9, {stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 1, lineDash: [1, 6]}}),
            overlayStyleRule(10, {stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 3, lineDash: [1, 10]}}),
            overlayStyleRule(11, {stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 5, lineDash: [1, 15]}}),
            overlayStyleRule(12, {stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 8, lineDash: [1, 22]}}),
            overlayStyleRule(13, {stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 8, lineDash: [1, 22]}}),
            overlayStyleRule(14, {stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 12, lineDash: [1, 28]}}),
            overlayStyleRule(15, {stroke: {opacity: 1.0, color: '#ffffff', lineCap: 'square', width: 12, lineDash: [1, 28]}})
        ];

        var cyclingAndWalkingSizeRules = [
            new StyleRule().where('zoomLevel').isIn([2,3,4]).use({stroke: {width: 8}}),
            new StyleRule().where('zoomLevel').isIn([5,6,7,8]).use({stroke: {width: 7}}),
            new StyleRule().where('zoomLevel').is(9).use({stroke: {width: 3}}),
            new StyleRule().where('zoomLevel').is(10).use({stroke: {width: 5}}),
            new StyleRule().where('zoomLevel').is(11).use({stroke: {width: 7}}),
            new StyleRule().where('zoomLevel').isIn([12,13]).use({stroke: {width: 10}}),
            new StyleRule().where('zoomLevel').isIn([14,15]).use({stroke: {width: 14}})
        ];

        me.browsingStyleProvider = new StyleRuleProvider({});
        me.browsingStyleProvider.addRules(linkStatusRules);
        me.browsingStyleProvider.addRules(cyclingAndWalkingStyleRules);
        me.browsingStyleProvider.addRules(cyclingAndWalkingSizeRules);
        me.browsingStyleProvider.addRules(featureTypeRules);
        me.browsingStyleProvider.addRules(overlayStyleRules);


    };
})(this);