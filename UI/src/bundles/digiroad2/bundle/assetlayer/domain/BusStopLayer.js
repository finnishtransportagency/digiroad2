Oskari.clazz.define('Oskari.digiroad2.bundle.assetlayer.domain.BusStopLayer',

        function() {
        this._layerType = "assetlayer";
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