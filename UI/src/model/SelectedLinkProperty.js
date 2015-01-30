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

    var moveTo = function(mmlId) {
      backend.getRoadLinkByMMLId(mmlId, function(response) {
        eventbus.trigger('coordinates:selected', {lon: response.middlePoint.x, lat: response.middlePoint.y});
        var id = response.id;
        if (collection.get(id)) {
          open(id);
          eventbus.trigger('ĺinkProperties:selectedAfterMove', id);
        } else {
          eventbus.once('roadLinks:afterDraw', function() {
            eventbus.trigger('ĺinkProperties:selectedAfterMove', id);
          });
        }
      });
    };

    return {
      close: close,
      open: open,
      isDirty: isDirty,
      getId: getId,
      get: get,
      save: save,
      cancel: cancel,
      moveTo: moveTo
    };
  };
})(this);
