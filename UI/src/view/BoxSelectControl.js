(function(root) {
  root.BoxSelectControl = function(map, doneFunction) {
    var getModifierKey = function() {
      if (navigator.platform.toLowerCase().indexOf('mac') === 0) {
        return OpenLayers.Handler.MOD_META;
      } else {
        return OpenLayers.Handler.MOD_CTRL;
      }
    };
    var boxControl = new OpenLayers.Control();
    map.addControl(boxControl);
    var boxHandler = new OpenLayers.Handler.Box(boxControl, { done: doneFunction }, { keyMask: getModifierKey() });

    var activate = function() { boxHandler.activate(); };
    var deactivate = function() { boxHandler.deactivate(); };
    return {
      activate: activate,
      deactivate: deactivate
    };
  };
})(this);
