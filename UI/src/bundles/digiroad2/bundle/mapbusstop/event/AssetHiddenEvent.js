Oskari.clazz.define('Oskari.digiroad2.bundle.mapbusstop.event.AssetHiddenEvent',
        function(id) {
        this._id = id;
    }, {
        __name : "mapbusstop.AssetHiddenEvent",
        getName : function() {
            return this.__name;
        },
        getId : function() {
            return this._id;
        }
    }, {
        'protocol' : ['Oskari.mapframework.event.Event']
    });
