(function(root) {
    root.VioniceLayer = function(params) {

        var layerName = 'vioniceLayer',
            map = params.map;

        var vioniceLayer = new ol.layer.Tile({source: new ol.source.TileWMS({
            url: 'vionice/api/v1/geoserver/vionice/wms',
            params: { 'LAYERS' : 'traffic-signs' }
        })});

        vioniceLayer.setVisible(false);
        vioniceLayer.set('name', layerName);

        var show = function(){
            _.each(map.getLayers().getArray(), function(layer){
                if (layer.get('name') === layerName) {
                    layer.setVisible(true);
                }
            });
        };

        var hide = function(){
            _.each(map.getLayers().getArray(), function(layer){
                if (layer.get('name') === layerName) {
                    layer.setVisible(false);
                }
            });
        };

        eventbus.on('map:showVioniceTrafficSign', show);
        eventbus.on('map:hideVioniceTrafficSign', hide);

        map.addLayer(vioniceLayer);

        return {
            show: show,
            hide: hide
        };
    };
})(this);



