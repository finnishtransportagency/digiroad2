(function(root) {
  root.DoubleClickSelectControl = function(layer, selectFunction, unselectFunction) {
    var selectControl = new OpenLayers.Control.SelectFeature(layer, {
      onSelect: selectFunction,
      onUnselect: unselectFunction
    });
    layer.map.addControl(selectControl);

    var selectClickHandler = new OpenLayers.Handler.Click(
      selectControl,
      {
        click: function(event) {
          var feature = selectControl.layer.getFeatureFromEvent(event);
          if (feature) {
            selectControl.select(feature);
          } else {
            selectControl.unselectAll();
          }
        },
        dblclick: function(event) {
          var feature = selectControl.layer.getFeatureFromEvent(event);
          if (feature) {
            selectControl.select(feature);
          } else {
            map.zoomIn();
          }
        }
      },
      {
        single: true,
        double: true,
        stopDouble: true,
        stopSingle: true
      }
    );
    var activate = function() {
      selectClickHandler.activate();
    };
    var deactivate = function() {
      selectClickHandler.deactivate();
    };

    return {
      activate: activate,
      deactivate: deactivate,
      getControl: function() { return selectControl; }
    };
  };
})(this);
