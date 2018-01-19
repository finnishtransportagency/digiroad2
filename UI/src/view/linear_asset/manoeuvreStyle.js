(function(root) {
    root.ManoeuvreStyle = function(roadLayer) {
        var zoomLevelRules = [
            new StyleRule().where('zoomLevel').is(9).use({ stroke: {width: 1 }}),
            new StyleRule().where('zoomLevel').is(10).use({ stroke: {width: 3 }}),
            new StyleRule().where('zoomLevel').is(11).use({ stroke: {width: 5 }}),
            new StyleRule().where('zoomLevel').is(12).use({ stroke: {width: 8 }}),
            new StyleRule().where('zoomLevel').is(13).use({ stroke: {width: 8 }}),
            new StyleRule().where('zoomLevel').is(14).use({ stroke: {width: 12 }}),
            new StyleRule().where('zoomLevel').is(15).use({ stroke: {width: 12 }})
        ];

        var featureTypeRules = [

            new StyleRule().where('type').is('overlay').use({ stroke: { color: '#ff0000'}}),
            new StyleRule().where('type').is('intermediate').use({ stroke: { color: '#11bb00', opacity: 1 }}),
            new StyleRule().where('type').is('sourceDestination').use({ stroke:{ color: '#000000', opacity: 1}}),

            new StyleRule().where('type').is('normal').and('zoomLevel').is(9).use({ stroke: {width: 2 }}),
            new StyleRule().where('type').is('normal').and('zoomLevel').is(10).use({ stroke: {width: 5 }}),
            new StyleRule().where('type').is('normal').and('zoomLevel').is(11).use({ stroke: {width: 7 }}),
            new StyleRule().where('type').is('normal').and('zoomLevel').is(12).use({ stroke: {width: 10 }}),
            new StyleRule().where('type').is('normal').and('zoomLevel').is(13).use({ stroke: {width: 10 }}),
            new StyleRule().where('type').is('normal').and('zoomLevel').is(14).use({ stroke: {width: 14 }}),
            new StyleRule().where('type').is('normal').and('zoomLevel').is(15).use({ stroke: {width: 14 }}),

            new StyleRule().where('type').is('multiple').use({ stroke:{ color: '#FFFFFF', lineCap: 'square', lineDash: [1, 6]}, fill : { color: '#FFFFFF'}}),
            new StyleRule().where('type').is('multiple').use({ stroke:{ color: '#FFFFFF', lineCap: 'square', lineDash: [1, 10]}, fill : { color: '#FFFFFF'}}),
            new StyleRule().where('type').is('multiple').use({ stroke:{ color: '#FFFFFF', lineCap: 'square', lineDash: [1, 15]}, fill : { color: '#FFFFFF'}}),
            new StyleRule().where('type').is('multiple').use({ stroke:{ color: '#FFFFFF', lineCap: 'square', lineDash: [1, 22]}, fill : { color: '#FFFFFF'}}),
            new StyleRule().where('type').is('multiple').use({ stroke:{ color: '#FFFFFF', lineCap: 'square', lineDash: [1, 22]}, fill : { color: '#FFFFFF'}}),
            new StyleRule().where('type').is('multiple').use({ stroke:{ color: '#FFFFFF', lineCap: 'square', lineDash: [1, 28]}, fill : { color: '#FFFFFF'}}),
            new StyleRule().where('type').is('multiple').use({ stroke:{ color: '#FFFFFF', lineCap: 'square', lineDash: [1, 28]}, fill : { color: '#FFFFFF'}}),

            new StyleRule().where('linkType').is(8).use({ stroke: { width: 5 }}),
            new StyleRule().where('linkType').is(9).use({ stroke: { width: 5 }}),
            new StyleRule().where('linkType').is(21).use({ stroke: { width: 5 }})
        ];


        var signSizeRules = [
            new StyleRule().where('zoomLevel', roadLayer.uiState).is(9).use({ pointRadius: 0 }),
            new StyleRule().where('zoomLevel', roadLayer.uiState).is(10).use({ pointRadius: 10 }),
            new StyleRule().where('zoomLevel', roadLayer.uiState).is(11).use({ pointRadius: 12 }),
            new StyleRule().where('zoomLevel', roadLayer.uiState).is(12).use({ pointRadius: 13 }),
            new StyleRule().where('zoomLevel', roadLayer.uiState).is(13).use({ pointRadius: 14 }),
            new StyleRule().where('zoomLevel', roadLayer.uiState).is(14).use({ pointRadius: 16 }),
            new StyleRule().where('zoomLevel', roadLayer.uiState).is(15).use({ pointRadius: 16 })
        ];

        var defaultStyleProvider = new StyleRuleProvider({stroke: {opacity: 0.65, rotation : '${rotation}'}});

        defaultStyleProvider.addRules([
            new StyleRule().where('manoeuvreSource').is(1).use({ stroke: {color: '#00f'}, icon: { src: 'images/link-properties/arrow-drop-blue.svg'}}),
            new StyleRule().where('manoeuvreSource').is(0).use({ stroke: {color: '#888'}, icon: { src: 'images/link-properties/arrow-drop-grey.svg'}})
        ]);
        defaultStyleProvider.addRules(zoomLevelRules);
        defaultStyleProvider.addRules(featureTypeRules);
        defaultStyleProvider.addRules([
            new StyleRule().where('zoomLevel').is(9).use({ pointRadius: 0 }),
            new StyleRule().where('zoomLevel').is(10).use({ pointRadius: 10 }),
            new StyleRule().where('zoomLevel').is(11).use({ pointRadius: 12 }),
            new StyleRule().where('zoomLevel').is(12).use({ pointRadius: 13 }),
            new StyleRule().where('zoomLevel').is(13).use({ pointRadius: 14 }),
            new StyleRule().where('zoomLevel').is(14).use({ pointRadius: 16 }),
            new StyleRule().where('zoomLevel').is(15).use({ pointRadius: 16 })
        ]);

        var selectionProvider = new StyleRuleProvider({stroke: {opacity: 0.9, rotation : '${rotation}', color: '#00f'},
                                            icon: {src: 'images/link-properties/arrow-drop-blue.svg'}});

        selectionProvider.addRules(zoomLevelRules);
        selectionProvider.addRules(featureTypeRules);
        selectionProvider.addRules(signSizeRules);

        var getDefaultStyle = function () {
            return defaultStyleProvider;
        };
        var getSelectedStyle = function () {
            return selectionProvider;
        };

        return {
            getDefaultStyle: getDefaultStyle,
            getSelectedStyle : getSelectedStyle
        };
    };
})(this);