(function(root) {
  //TODO if no need for DoubleClickSelectControl
  root.DoubleClickSelectControl = function(layer, map, linearAssetOnSelect, styleFn, vectorOpacity) {
    var eventKey;
    var enabled = false;


    var selectClickHandler = new ol.interaction.Select({
      layer: layer,
      condition: function(events){
        return enabled && (ol.events.condition.doubleClick(events) || ol.events.condition.singleClick(events));
      },
      style: styleFn
    });

    selectClickHandler.on('select',  function(evt){
        if(evt.selected.length > 0)
            layer.setOpacity(vectorOpacity);
        else
            layer.setOpacity(1);

        linearAssetOnSelect(evt);
    });

    var doubleClick = function () {
      _.defer(function(){
        if(selectClickHandler.getFeatures().getLength() < 1 && map.getView().getZoom() <= 13){
           map.getView().setZoom(map.getView().getZoom()+1);
        }
      });
    };

  map.addInteraction(selectClickHandler);

    var activate = function() {
       enabled = true;
       eventKey = map.on('dblclick', doubleClick);

    };
    var deactivate = function() {
        enabled = false;
       //map.removeInteraction(selectClickHandler);
       map.unByKey(eventKey);
    };

    var clear = function(){
        selectClickHandler.getFeatures().clear();
        layer.setOpacity(1);
    };

    return {
      getInteraction: function(){ return selectClickHandler; },
      activate: activate,
      deactivate: deactivate,
      clear: clear
    };
  };
})(this);
