(function(root) {
  root.BoxSelectControl = function(map, onStart, onEnd) {

    var dragBox = new ol.interaction.DragBox({
      condition: ol.events.condition.platformModifierKeyOnly
    });

    dragBox.on('boxstart', onStart);
    dragBox.on('boxend', function() {
        var extent = dragBox.getGeometry().getExtent();
        onEnd(extent);
    });

    var activate = function() { map.addInteraction(dragBox); };
    var deactivate = function() { map.removeInteraction(dragBox); };
    return {
      activate: activate,
      deactivate: deactivate
    };
  };
})(this);
