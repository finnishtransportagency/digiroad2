(function(root) {
  //TODO if no need for BoxSelectControl
  root.BoxSelectControl = function(map, layer, onStart, onEnd) {

    var dragBox = new ol.interaction.DragBox({
      condition: ol.events.condition.platformModifierKeyOnly
    });

    dragBox.on('boxstart', onStart);
    dragBox.on('boxend', function() {
      var extent = dragBox.getGeometry().getExtent();
      var selectedFeatures = [];
      layer.getSource().forEachFeatureIntersectingExtent(extent, function (feature) {
        selectedFeatures.push(feature.values_);
      });
      onEnd(selectedFeatures);
    });

    var activate = function() { map.addInteraction(dragBox); };
    var deactivate = function() { map.removeInteraction(dragBox); };
    return {
      activate: activate,
      deactivate: deactivate
    };
  };
})(this);
