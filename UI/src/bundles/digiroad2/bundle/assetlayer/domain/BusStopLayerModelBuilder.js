Oskari.clazz.define('Oskari.digiroad2.bundle.assetlayer.domain.BusStopLayerModelBuilder', function (sandbox) {
    this.sandbox = sandbox;
}, {
    parseLayerData: function (layer, mapLayerJson, maplayerService) {

        layer.addLayerUrl(mapLayerJson.url);
        layer.setRoadLinesUrl(mapLayerJson.roadLinesUrl);

    }
});