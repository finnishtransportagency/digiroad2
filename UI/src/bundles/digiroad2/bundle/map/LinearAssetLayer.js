window.LinearAssetLayer = function(map, roadLayer) {
    var vectorLayer = new OpenLayers.Layer.Vector("linearAsset", {
        styleMap: new OpenLayers.StyleMap({
            "default": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
                strokeColor: "#333399",
                strokeWidth: 8
            }, OpenLayers.Feature.Vector.style["default"])),
            "select": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
                strokeColor: "#3333ff",
                strokeWidth: 12
            }, OpenLayers.Feature.Vector.style["select"]))
        })
    });
    vectorLayer.setOpacity(0.5);

    var markerLayer = new OpenLayers.Layer.Markers('marker');

    map.addLayer(vectorLayer);
    map.addLayer(markerLayer);
}
