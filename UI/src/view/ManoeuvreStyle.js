(function(root) {
    root.ManoeuvreStyle = function(roadLayer) {
        // var featureTypeRules = [
        //     new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeWidth: 2 }),
        //     new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeWidth: 5 }),
        //     new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeWidth: 7 }),
        //     new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(12).use({ strokeWidth: 10 }),
        //     new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(13).use({ strokeWidth: 10 }),
        //     new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(14).use({ strokeWidth: 14 }),
        //     new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(15).use({ strokeWidth: 14 }),
        //     new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeColor: '#ff0000', strokeWidth: 1 }),
        //     new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#ff0000', strokeWidth: 3 }),
        //     new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#ff0000', strokeWidth: 5 }),
        //     new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(12).use({ strokeColor: '#ff0000', strokeWidth: 8 }),
        //     new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(13).use({ strokeColor: '#ff0000', strokeWidth: 8 }),
        //     new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(14).use({ strokeColor: '#ff0000', strokeWidth: 12 }),
        //     new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(15).use({ strokeColor: '#ff0000', strokeWidth: 12 }),
        //     new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeColor: '#11bb00', strokeWidth: 1, strokeOpacity: 1 }),
        //     new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#11bb00', strokeWidth: 3, strokeOpacity: 1 }),
        //     new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#11bb00', strokeWidth: 5, strokeOpacity: 1 }),
        //     new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(12).use({ strokeColor: '#11bb00', strokeWidth: 8, strokeOpacity: 1 }),
        //     new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(13).use({ strokeColor: '#11bb00', strokeWidth: 8, strokeOpacity: 1 }),
        //     new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(14).use({ strokeColor: '#11bb00', strokeWidth: 12, strokeOpacity: 1 }),
        //     new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(15).use({ strokeColor: '#11bb00', strokeWidth: 12, strokeOpacity: 1 }),
        //     new OpenLayersRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 6' }),
        //     new OpenLayersRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 3, strokeDashstyle: '1 10' }),
        //     new OpenLayersRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 5, strokeDashstyle: '1 15' }),
        //     new OpenLayersRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(12).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
        //     new OpenLayersRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(13).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
        //     new OpenLayersRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(14).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' }),
        //     new OpenLayersRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(15).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' }),
        //     new OpenLayersRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeColor: '#000000', strokeOpacity: 1, strokeWidth: 1 }),
        //     new OpenLayersRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#000000',strokeOpacity: 1,  strokeWidth: 3 }),
        //     new OpenLayersRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#000000',strokeOpacity: 1,  strokeWidth: 5 }),
        //     new OpenLayersRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(12).use({ strokeColor: '#000000',strokeOpacity: 1,  strokeWidth: 8 }),
        //     new OpenLayersRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(13).use({ strokeColor: '#000000',strokeOpacity: 1,  strokeWidth: 8 }),
        //     new OpenLayersRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(14).use({ strokeColor: '#000000',strokeOpacity: 1,  strokeWidth: 12 }),
        //     new OpenLayersRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(15).use({ strokeColor: '#000000',strokeOpacity: 1,  strokeWidth: 12 }),
        //     new OpenLayersRule().where('linkType').is(8).use({ strokeWidth: 5 }),
        //     new OpenLayersRule().where('linkType').is(9).use({ strokeWidth: 5 }),
        //     new OpenLayersRule().where('linkType').is(21).use({ strokeWidth: 5 })
    //     ];
    //     var signSizeRules = [
    //         new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(9).use({ pointRadius: 0 }),
    //         new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(10).use({ pointRadius: 10 }),
    //         new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(11).use({ pointRadius: 12 }),
    //         new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(12).use({ pointRadius: 13 }),
    //         new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(13).use({ pointRadius: 14 }),
    //         new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(14).use({ pointRadius: 16 }),
    //         new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(15).use({ pointRadius: 16 })
    //     ];
    //     var defaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
    //         strokeOpacity: 0.65,
    //         pointRadius: 12,
    //         rotation: '${rotation}',
    //         graphicOpacity: 1.0
    //     }));
    //     defaultStyle.addRules([
    //         new OpenLayersRule().where('manoeuvreSource').is(1).use({ strokeColor: '#00f', externalGraphic: 'images/link-properties/arrow-drop-blue.svg' }),
    //         new OpenLayersRule().where('manoeuvreSource').is(0).use({ strokeColor: '#888', externalGraphic: 'images/link-properties/arrow-drop-grey.svg' })
    //     ]);
    //     defaultStyle.addRules(featureTypeRules);
    //     defaultStyle.addRules([
    //         new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(9).use({ pointRadius: 0 }),
    //         new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(10).use({ pointRadius: 10 }),
    //         new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(11).use({ pointRadius: 12 }),
    //         new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(12).use({ pointRadius: 13 }),
    //         new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(13).use({ pointRadius: 14 }),
    //         new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(14).use({ pointRadius: 16 }),
    //         new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(15).use({ pointRadius: 16 })
    //     ]);
    //     var defaultStyleMap = new OpenLayers.StyleMap({ default: defaultStyle });
    //
    //     var selectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
    //         strokeOpacity: 0.9,
    //         pointRadius: 12,
    //         rotation: '${rotation}',
    //         graphicOpacity: 1.0,
    //         strokeColor: '#00f',
    //         externalGraphic: 'images/link-properties/arrow-drop-blue.svg'
    //     }));
    //     var selectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
    //         strokeOpacity: 0.15,
    //         pointRadius: 12,
    //         rotation: '${rotation}',
    //         graphicOpacity: 0.15,
    //         strokeColor: '#888',
    //         externalGraphic: 'images/link-properties/arrow-drop-grey.svg'
    //     }));
    //     selectionDefaultStyle.addRules(featureTypeRules);
    //     selectionSelectStyle.addRules(featureTypeRules);
    //     selectionDefaultStyle.addRules(signSizeRules);
    //     selectionSelectStyle.addRules(signSizeRules);
    //     selectionDefaultStyle.addRules([
    //         new OpenLayersRule().where('adjacent').is(0).use({ strokeOpacity: 0.15, graphicOpacity: 0.15 }),
    //         new OpenLayersRule().where('adjacent').is(1).use({ strokeOpacity: 0.9, graphicOpacity: 1.0 })
    //     ]);
    //     var selectionStyleMap = new OpenLayers.StyleMap({
    //         select:  selectionSelectStyle,
    //         default: selectionDefaultStyle
    //     });
    //
    //     return {
    //         defaultStyleMap: defaultStyleMap,
    //         selectionStyleMap: selectionStyleMap
    //     };
    //     var featureTypeRules = [
    //             new StyleRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(9).use({ stroke: {width: 2 }}),
    //             new StyleRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(10).use({ stroke: {width: 5 }}),
    //             new StyleRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(11).use({ stroke: {width: 7 }}),
    //             new StyleRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(12).use({ stroke: {width: 10 }}),
    //             new StyleRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(13).use({ stroke: {width: 10 }}),
    //             new StyleRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(14).use({ stroke: {width: 14 }}),
    //             new StyleRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(15).use({ stroke: {width: 14 }}),
    //             new StyleRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(9).use({ stroke: { color: '#ff0000', width: 1 }}),
    //             new StyleRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(10).use({ stroke:{ color: '#ff0000', width: 3 }}),
    //             new StyleRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(11).use({ stroke:{ color: '#ff0000', width: 5 }}),
    //             new StyleRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(12).use({ stroke:{ color: '#ff0000', width: 8 }}),
    //             new StyleRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(13).use({ stroke:{ color: '#ff0000', width: 8 }}),
    //             new StyleRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(14).use({ stroke:{ color: '#ff0000', width: 12 }}),
    //             new StyleRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(15).use({ stroke:{ color: '#ff0000', width: 12 }}),
    //             new StyleRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(9).use({ stroke:{ color: '#11bb00', width: 1, opacity: 1 }}),
    //             new StyleRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(10).use({ stroke:{ color: '#11bb00', width: 3, opacity: 1 }}),
    //             new StyleRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(11).use({ stroke:{ color: '#11bb00', width: 5, opacity: 1 }}),
    //             new StyleRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(12).use({ stroke:{ color: '#11bb00', width: 8, opacity: 1 }}),
    //             new StyleRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(13).use({ stroke:{ color: '#11bb00', width: 8, opacity: 1 }}),
    //             new StyleRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(14).use({ stroke:{ color: '#11bb00', width: 12, opacity: 1 }}),
    //             new StyleRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(15).use({ stroke:{ color: '#11bb00', width: 12, opacity: 1 }}),
    //             new StyleRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(9).use({ stroke:{ color: '#FFFFFF', linecap: 'square', width: 1, lineDash: [1, 6] }}),
    //             new StyleRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(10).use({ stroke:{ color: '#FFFFFF', linecap: 'square', width: 3, lineDash: [1, 10] }}),
    //             new StyleRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(11).use({ stroke:{ color: '#FFFFFF', linecap: 'square', width: 5, lineDash: [1, 15] }}),
    //             new StyleRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(12).use({ stroke:{ color: '#FFFFFF', linecap: 'square', width: 8, lineDash: [1, 22] }}),
    //             new StyleRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(13).use({ stroke:{ color: '#FFFFFF', linecap: 'square', width: 8, lineDash: [1, 22] }}),
    //             new StyleRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(14).use({ stroke:{ color: '#FFFFFF', linecap: 'square', width: 12, lineDash: [1, 28] }}),
    //             new StyleRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(15).use({ stroke:{ color: '#FFFFFF', linecap: 'square', width: 12, lineDash: [1, 28] }}),
    //             new StyleRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(9).use({ stroke:{ color: '#000000', opacity: 1, width: 1 }}),
    //             new StyleRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(10).use({ stroke:{ color: '#000000', opacity: 1, width: 3 }}),
    //             new StyleRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(11).use({ stroke:{ color: '#000000', opacity: 1, width: 5 }}),
    //             new StyleRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(12).use({ stroke:{ color: '#000000', opacity: 1, width: 8 }}),
    //             new StyleRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(13).use({ stroke:{ color: '#000000', opacity: 1, width: 8 }}),
    //             new StyleRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(14).use({ stroke:{ color: '#000000', opacity: 1, width: 12 }}),
    //             new StyleRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(15).use({ stroke:{ color: '#000000', opacity: 1, width: 12 }}),
    //             new StyleRule().where('linkType').is(8).use({ stroke: { width: 5 }}),
    //             new StyleRule().where('linkType').is(9).use({ stroke: { width: 5 }}),
    //             new StyleRule().where('linkType').is(21).use({ stroke: { width: 5 }})
    //         ];

        //First to add to addRules of provider
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
        // var defaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        //     strokeOpacity: 0.65,
        //     pointRadius: 12,
        //     rotation: '${rotation}',
        //     graphicOpacity: 1.0
        // }));

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
        //var defaultStyleMap = new OpenLayers.StyleMap({ default: defaultStyle });
        //
        // var selectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        //     strokeOpacity: 0.9,
        //     pointRadius: 12,
        //     rotation: '${rotation}',
        //     graphicOpacity: 1.0,
        //     strokeColor: '#00f',
        //     externalGraphic: 'images/link-properties/arrow-drop-blue.svg'
        // }));
        // var selectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        //     strokeOpacity: 0.15,
        //     pointRadius: 12,
        //     rotation: '${rotation}',
        //     graphicOpacity: 0.15,
        //     strokeColor: '#888',
        //     externalGraphic: 'images/link-properties/arrow-drop-grey.svg'
        // }));

        var selectionDefaultProvider = new StyleRuleProvider({stroke: {opacity: 0.15, rotation : '${rotation}'},
                                                    icon: {src: 'images/link-properties/arrow-drop-grey.svg'}});


        var selectionProvider = new StyleRuleProvider({stroke: {opacity: 0.9, rotation : '${rotation}', color: '#00f'},
                                            icon: {src: 'images/link-properties/arrow-drop-blue.svg'}});

        // selectionDefaultProvider.addRules(selectionStyle);
        selectionDefaultProvider.addRules(zoomLevelRules);
        selectionDefaultProvider.addRules(featureTypeRules);
        selectionDefaultProvider.addRules(signSizeRules);
        selectionDefaultProvider.addRules([
            new StyleRule().where('adjacent').is(0).use({ stroke: {opacity: 0.15, graphicOpacity: 0.15 }}),
            new StyleRule().where('adjacent').is(1).use({ stroke: {opacity: 0.9, graphicOpacity: 1.0 }})
        ]);

        // selectionDefaultProvider.addRules(selectionStyle);
        // selectionDefaultProvider.addRules(zoomLevelRules);
        // selectionProvider.addRules(featureTypeRules);
        // selectionProvider.addRules(signSizeRules);

        selectionProvider.addRules(zoomLevelRules);
        selectionProvider.addRules(featureTypeRules);
        selectionProvider.addRules(signSizeRules);

        // selectionDefaultStyle.addRules(featureTypeRules);
        // selectionSelectStyle.addRules(featureTypeRules);
        // selectionDefaultStyle.addRules(signSizeRules);
        // selectionSelectStyle.addRules(signSizeRules);
        // selectionDefaultStyle.addRules([
        //     new OpenLayersRule().where('adjacent').is(0).use({ strokeOpacity: 0.15, graphicOpacity: 0.15 }),
        //     new OpenLayersRule().where('adjacent').is(1).use({ strokeOpacity: 0.9, graphicOpacity: 1.0 })
        // ]);
        // var selectionStyleMap = new OpenLayers.StyleMap({
        //     select:  selectionSelectStyle,
        //     default: selectionDefaultStyle
        // });

        var getDefaultStyle = function () {
            return defaultStyleProvider;
        };
        //
        var getSelectedStyle = function () {
            return selectionProvider;
            //     selectionDefaultProvider: selectionDefaultProvider
            // };
        };


        return {
            getDefaultStyle: getDefaultStyle,
            getSelectedStyle : getSelectedStyle
        };
    };
})(this);