(function(root) {
    root.HybridAssetLayer=function(params){
        var map = params.map,
            application = params.application,
            collection = params.collection,
            selectedLinearAsset = params.selectedLinearAsset,
            roadLayer = params.roadLayer,
            multiElementEventCategory = params.multiElementEventCategory,
            singleElementEventCategory = params.singleElementEventCategory,
            style = params.style,
            layerName = params.layerName,
            assetLabel = params.assetLabel,
            roadAddressInfoPopup = params.roadAddressInfoPopup,
            massLimitation = params.massLimitation,
            trafficSignReadOnlyLayer = params.readOnlyLayer,
            isMultipleLinkSelectionAllowed = params.isMultipleLinkSelectionAllowed,
            authorizationPolicy = params.authorizationPolicy,
            isExperimental = params.isExperimental,
            minZoomForContent = params.minZoomForContentroadCollection = params.roadCollection,
            selectedAsset = params.selectedAsset,
            mapOverlay = params.mapOverlay,
            newAsset = params.newAsset,
            allowGrouping = params.allowGrouping,
            assetGrouping = params.assetGrouping;
        var mixing =Object.assign(LinearAssetLayer,PointAssetLayer)
        mixing.call(this, layerName,roadLayer)
        var me = this;
        this.minZoomForContent = isExperimental && minZoomForContent ? minZoomForContent : zoomlevels.minZoomForAssets;
        var isComplementaryChecked = false;
        var extraEventListener = _.extend({running: false}, eventbus);

        return {
            vectorLayer: me.vectorLayer,
            show: me.showLayer,
            hide: me.hideLayer,
            minZoomForContent: me.minZoomForContent
        };
    };/**/
})(this);

