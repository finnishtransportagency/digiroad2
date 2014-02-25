Oskari.clazz.define('Oskari.digiroad2.bundle.mapbusstop.event.AssetModifiedEvent',
    function(asset) {
        this._asset = asset;
    }, {__name : "mapbusstop.AssetModifiedEvent",

        getName : function() {
            return this.__name;
        },

        getAsset : function() {
            return this._asset;
        }

    }, {
        'protocol' : ['Oskari.mapframework.event.Event']
    });
