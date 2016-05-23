(function(root) {
    root.ManoeuvreStyle = function(roadLayer) {
        var featureTypeRules = [
            new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeWidth: 2 }),
            new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeWidth: 5 }),
            new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeWidth: 7 }),
            new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(12).use({ strokeWidth: 10 }),
            new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(13).use({ strokeWidth: 10 }),
            new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(14).use({ strokeWidth: 14 }),
            new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(15).use({ strokeWidth: 14 }),
            new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeColor: '#ff0000', strokeWidth: 1 }),
            new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#ff0000', strokeWidth: 3 }),
            new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#ff0000', strokeWidth: 5 }),
            new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(12).use({ strokeColor: '#ff0000', strokeWidth: 8 }),
            new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(13).use({ strokeColor: '#ff0000', strokeWidth: 8 }),
            new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(14).use({ strokeColor: '#ff0000', strokeWidth: 12 }),
            new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(15).use({ strokeColor: '#ff0000', strokeWidth: 12 }),
            new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeColor: '#11bb00', strokeWidth: 1 }),
            new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#11bb00', strokeWidth: 3 }),
            new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#11bb00', strokeWidth: 5 }),
            new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(12).use({ strokeColor: '#11bb00', strokeWidth: 8 }),
            new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(13).use({ strokeColor: '#11bb00', strokeWidth: 8 }),
            new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(14).use({ strokeColor: '#11bb00', strokeWidth: 12 }),
            new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(15).use({ strokeColor: '#11bb00', strokeWidth: 12 }),
            new OpenLayersRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 6' }),
            new OpenLayersRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 3, strokeDashstyle: '1 10' }),
            new OpenLayersRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 5, strokeDashstyle: '1 15' }),
            new OpenLayersRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(12).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
            new OpenLayersRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(13).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
            new OpenLayersRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(14).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' }),
            new OpenLayersRule().where('type').is('multiple').and('zoomLevel', roadLayer.uiState).is(15).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' }),
            new OpenLayersRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeColor: '#000000', strokeWidth: 1 }),
            new OpenLayersRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#000000', strokeWidth: 3 }),
            new OpenLayersRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#000000', strokeWidth: 5 }),
            new OpenLayersRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(12).use({ strokeColor: '#000000', strokeWidth: 8 }),
            new OpenLayersRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(13).use({ strokeColor: '#000000', strokeWidth: 8 }),
            new OpenLayersRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(14).use({ strokeColor: '#000000', strokeWidth: 12 }),
            new OpenLayersRule().where('type').is('sourceDestination').and('zoomLevel', roadLayer.uiState).is(15).use({ strokeColor: '#000000', strokeWidth: 12 }),
            new OpenLayersRule().where('linkType').is(8).use({ strokeWidth: 5 }),
            new OpenLayersRule().where('linkType').is(9).use({ strokeWidth: 5 }),
            new OpenLayersRule().where('linkType').is(21).use({ strokeWidth: 5 })
        ];
        var signSizeRules = [
            new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(9).use({ pointRadius: 0 }),
            new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(10).use({ pointRadius: 10 }),
            new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(11).use({ pointRadius: 12 }),
            new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(12).use({ pointRadius: 13 }),
            new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(13).use({ pointRadius: 14 }),
            new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(14).use({ pointRadius: 16 }),
            new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(15).use({ pointRadius: 16 })
        ];
        var defaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
            strokeOpacity: 0.65,
            pointRadius: 12,
            rotation: '${rotation}',
            graphicOpacity: 1.0
        }));
        defaultStyle.addRules([
            new OpenLayersRule().where('manoeuvreSource').is(1).use({ strokeColor: '#00f', externalGraphic: 'images/link-properties/arrow-drop-blue.svg' }),
            new OpenLayersRule().where('manoeuvreSource').is(0).use({ strokeColor: '#888', externalGraphic: 'images/link-properties/arrow-drop-grey.svg' })
        ]);
        defaultStyle.addRules(featureTypeRules);
        defaultStyle.addRules([
            new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(9).use({ pointRadius: 0 }),
            new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(10).use({ pointRadius: 10 }),
            new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(11).use({ pointRadius: 12 }),
            new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(12).use({ pointRadius: 13 }),
            new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(13).use({ pointRadius: 14 }),
            new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(14).use({ pointRadius: 16 }),
            new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(15).use({ pointRadius: 16 })
        ]);
        var defaultStyleMap = new OpenLayers.StyleMap({ default: defaultStyle });

        var selectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
            strokeOpacity: 0.9,
            pointRadius: 12,
            rotation: '${rotation}',
            graphicOpacity: 1.0,
            strokeColor: '#00f',
            externalGraphic: 'images/link-properties/arrow-drop-blue.svg'
        }));
        var selectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
            strokeOpacity: 0.15,
            pointRadius: 12,
            rotation: '${rotation}',
            graphicOpacity: 0.15,
            strokeColor: '#888',
            externalGraphic: 'images/link-properties/arrow-drop-grey.svg'
        }));
        selectionDefaultStyle.addRules(featureTypeRules);
        selectionSelectStyle.addRules(featureTypeRules);
        selectionDefaultStyle.addRules(signSizeRules);
        selectionSelectStyle.addRules(signSizeRules);
        selectionDefaultStyle.addRules([
            new OpenLayersRule().where('adjacent').is(0).use({ strokeOpacity: 0.15, graphicOpacity: 0.15 }),
            new OpenLayersRule().where('adjacent').is(1).use({ strokeOpacity: 0.9, graphicOpacity: 1.0 })
        ]);
        var selectionStyleMap = new OpenLayers.StyleMap({
            select:  selectionSelectStyle,
            default: selectionDefaultStyle
        });

        return {
            defaultStyleMap: defaultStyleMap,
            selectionStyleMap: selectionStyleMap
        };
    };
})(this);