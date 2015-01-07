(function(root) {
  root.SelectedLinkProperty = function(backend, collection) {
    var current = null;

    var close = function() {
      if (current && !current.isDirty()) {
        current.unselect();
        current = null;
      }
    };

    var open = function(id) {
      close();
      current = collection.get(id);
      current.select();
    };

    var isDirty = function() {
      return current && current.isDirty();
    };

    var setTrafficDirection = function(trafficDirection) {
      current.setTrafficDirection(trafficDirection);
    };

    var getId = function() {
      return current && current.getId();
    };

    var get = function() {
      return current;
    };

    var cancel = function() {
      current.cancel();
    };

    return {
      close: close,
      open: open,
      setTrafficDirection: setTrafficDirection,
      isDirty: isDirty,
      getId: getId,
      get: get,
      cancel: cancel
    };
  };
})(this);
