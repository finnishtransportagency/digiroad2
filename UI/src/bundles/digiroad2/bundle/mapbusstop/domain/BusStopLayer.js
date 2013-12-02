/**
 * @class Oskari.digiroad2.bundle.mapbusstop.domain.BusStopLayer
 *
 * MapLayer of type Stats
 */
Oskari.clazz.define('Oskari.digiroad2.bundle.mapbusstop.domain.BusStopLayer',

    /**
     * @method create called automatically on construction
     * @static
     */
        function() {
        /* Layer Type */
        this._layerType = "busstoplayer";
        this._roadLinesUrl = "";
        this._layerUrls = [];

    }, {

        setRoadLinesUrl : function(roadLinesUrl) {
            this._roadLinesUrl = roadLinesUrl;
        },

        getRoadLinesUrl : function() {
            return this._roadLinesUrl;
        },

        addLayerUrl : function(layerUrl) {
            this._layerUrls.push(layerUrl);
        },

        getLayerUrls : function() {
            return this._layerUrls;
        }

    }, {
        "extend": ["Oskari.mapframework.domain.AbstractLayer"]
    });