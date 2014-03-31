Oskari.clazz.define('Oskari.digiroad2.bundle.map.domain.LinearAsset',

    function() {
        this._layerType = "linearassetlayer";
        this._layerUrls = [];

    }, {

        addLayerUrl : function(layerUrl) {
            this._layerUrls.push(layerUrl);
        },

        getLayerUrls : function() {
            return this._layerUrls;
        }

    }, {
        "extend": ["Oskari.mapframework.domain.AbstractLayer"]
    });