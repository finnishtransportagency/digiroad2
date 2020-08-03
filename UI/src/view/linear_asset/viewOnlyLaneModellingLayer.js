(function (root) {
    root.ViewOnlyLaneModellingLayer = function (params, map) {
        var style = params.style;
        var collection = params.collection;
        var isComplementaryChecked;

        var uiState = { zoomLevel: 9 };

        var vectorSource = new ol.source.Vector();
        var vectorLayer = new ol.layer.Vector({
            source : vectorSource,
            style : function(feature) {
                return style.browsingStyleProviderViewOnly.getStyle(feature, {zoomLevel: uiState.zoomLevel});
            },
            zIndex: 1
        });

        vectorLayer.set('name', 'ViewOnlyLaneModellingLayer');
        vectorLayer.setOpacity(1);
        vectorLayer.setVisible(true);
        map.addLayer(vectorLayer);

        var adjustStylesByZoomLevel = function(zoom) {
            uiState.zoomLevel = zoom;
        };

        var showWithComplementary = function () {
            isComplementaryChecked = true;
        };

        var hideComplementary = function(){
            isComplementaryChecked = false;
        };

        var showLayer = function(){
            vectorLayer.setOpacity(1);
        };

        var hideLayer = function(){
            vectorLayer.setOpacity(0.15);
        };

        var removeLayerFeatures = function(){
            vectorLayer.getSource().clear();
        };

        var redrawLinearAssets = function(linearAssetChains) {
            vectorSource.clear();
            var linearAssets = _.flatten(linearAssetChains);
            drawLinearAssets(linearAssets);
        };

        var drawLinearAssets = function(linearAssets) {
            vectorSource.addFeatures(style.renderFeatures(linearAssets));
        };

        var refreshView = function () {
            vectorLayer.setVisible(true);
            adjustStylesByZoomLevel(zoomlevels.getViewZoom(map));
            collection.fetchViewOnlyLanes(map.getView().calculateExtent(map.getSize()), Math.round(map.getView().getZoom()));
        };

        eventbus.on('fetchedViewOnly', redrawLinearAssets);

        return {
            refreshView: refreshView,
            redrawLinearAssets: redrawLinearAssets,
            hideLayer: hideLayer,
            showLayer: showLayer,
            removeLayerFeatures: removeLayerFeatures,
            showWithComplementary: showWithComplementary,
            hideComplementary: hideComplementary
        };
    };
})(this);


