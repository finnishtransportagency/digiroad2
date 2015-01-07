(function(root) {
  root.SelectedLinkProperty = function(backend, collection) {
    var current = null;

    var close = function() {
      if (current && !current.isDirty()) {
        current = null;
        eventbus.trigger('linkProperties:unselected');
      }
    };

    var open = function(id) {
      close();
      current = collection.get(id);
      eventbus.trigger('linkProperties:selected', current.getData());
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
