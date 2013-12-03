/*
 * @class Oskari.digiroad2.bundle.mapbusstop.domain.BusStopLayerModelBuilder
 * JSON-parsing for arcgis layer
 */
Oskari.clazz.define('Oskari.digiroad2.bundle.mapbusstop.domain.BusStopLayerModelBuilder', function (sandbox) {
    this.sandbox = sandbox;
}, {
    /**
     * parses any additional fields to model
     * @param {Oskari.digiroad2.bundle.mapbusstop.domain.BusStopLayer} layer partially populated layer
     * @param {Object} mapLayerJson JSON presentation of the layer
     * @param {Oskari.mapframework.service.MapLayerService} maplayerService not really needed here
     */
    parseLayerData: function (layer, mapLayerJson, maplayerService) {

        layer.addLayerUrl(mapLayerJson.url);
        layer.setRoadLinesUrl(mapLayerJson.roadLinesUrl);

    }
});