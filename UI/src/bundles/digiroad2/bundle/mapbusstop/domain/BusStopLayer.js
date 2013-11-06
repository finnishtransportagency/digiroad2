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

    }, {

    }, {
        "extend": ["Oskari.mapframework.domain.AbstractLayer"]
    });