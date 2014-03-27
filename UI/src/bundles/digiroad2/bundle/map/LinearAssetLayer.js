
window.LinearAssetLayer = function(map, roadLayer) {

    var selectControl;
    var modifyControls;

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

    // TODO: Instead of a success callback fire an event
    jQuery.get('/data/SpeedLimits.json', function(lineAssets) {
        var features = _.map(lineAssets, function(lineAsset) {
            var points = _.map(lineAsset.coordinates, function(coordinate) {
                return new OpenLayers.Geometry.Point(coordinate[0], coordinate[1]);
            });
            return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), null);
        });
        vectorLayer.addFeatures(features);
    });

    map.addLayer(vectorLayer);

    modifyControls = {
        modify : new OpenLayers.Control.ModifyFeature(vectorLayer, {
            standalone: true
        })
    };

    selectControl = new OpenLayers.Control.SelectFeature(
        [vectorLayer],
        {
            geometryTypes: modifyControls.modify.geometryTypes,
            clickout: modifyControls.modify.clickout,
            toggle: modifyControls.modify.toggle,
            onBeforeSelect: modifyControls.modify.beforeSelectFeature,
            onSelect: modifyControls.modify.selectFeature,
            onUnselect: modifyControls.modify.unselectFeature,
            scope: modifyControls.modify
        }
    );
    map.addControl(selectControl);
    selectControl.activate();

    for(var key in modifyControls) {
        map.addControl(modifyControls[key]);
    }
    // no harm in activating straight away
    modifyControls.modify.activate();
}

