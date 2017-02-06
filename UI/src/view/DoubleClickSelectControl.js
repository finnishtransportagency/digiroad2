(function(root) {
  root.DoubleClickSelectControl = function(layer, map, linearAssetOnSelect) {
    var eventKey;

    var selectClickHandler = new ol.interaction.Select({
      layer: layer,
      condition: function(events){
         return ol.events.condition.doubleClick(events) || ol.events.condition.singleClick(events);
      }
    });

    selectClickHandler.on('select', linearAssetOnSelect);

    var doubleClick = function () {
      _.defer(function(){
        if(selectClickHandler.getFeatures().getLength() < 1 && map.getView().getZoom() <= 13){
           map.getView().setZoom(map.getView().getZoom()+1);
        }
      });
    };

    var activate = function() {
       map.addInteraction(selectClickHandler);
       eventKey = map.on('dblclick', doubleClick);

    };
    var deactivate = function() {
       map.removeInteraction(selectClickHandler);
       map.unByKey(eventKey);
    };

    return {
      activate: activate,
      deactivate: deactivate
    };
  };
})(this);
