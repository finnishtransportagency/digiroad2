(function(root) {
  root.SelectedLinkProperty = function(backend, collection) {
    var current = null;

    var close = function() {
      if (current && !current.isDirty()) {
        current.unselect();
        eventbus.trigger('linkProperties:unselected');
        current = null;
      }
    };

    var open = function(id) {
      if (id !== getId()) {
        close();
        current = collection.get(id);
        current.select();
        eventbus.trigger('linkProperties:selected', current.getData());
      }
    };

    var isDirty = function() {
      return current && current.isDirty();
    };

    var getId = function() {
      return current && current.getId();
    };

    var get = function() {
      return current;
    };

    var save = function() {
      current.save(backend);
    };

    var cancel = function() {
      current.cancel();
    };

    return {
      close: close,
      open: open,
      isDirty: isDirty,
      getId: getId,
      get: get,
      save: save,
      cancel: cancel
    };
  };
})(this);
