(function(root) {
  root.SelectedLinkProperty = function(backend, collection) {
    var dirty = false;
    var current = null;

    var close = function() {
      if (current && !dirty) {
        current = null;
        eventbus.trigger('linkProperties:unselected');
      }
    };

    var open = function(id) {
      close();
      var roadLink = collection.get(id);
      current = roadLink;
      eventbus.trigger('linkProperties:selected', roadLink);
    };

    var isDirty = function() {
      return dirty;
    };

    var setTrafficDirection = function(trafficDirection) {
      if (trafficDirection != current.trafficDirection) {
        current.trafficDirection = trafficDirection;
        dirty = true;
        eventbus.trigger('linkProperties:changed');
      }
    };

    var getId = function() {
      return current && current.roadLinkId;
    };

    var get = function() {
      return current;
    };

    return {
      close: close,
      open: open,
      setTrafficDirection: setTrafficDirection,
      isDirty: isDirty,
      getId: getId,
      get: get
    };
  };
})(this);