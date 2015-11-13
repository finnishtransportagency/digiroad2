(function(root) {
  root.SelectedPointAsset = function(backend, collection) {
    var current = null;

    return {
      open: open,
      getId: getId,
      asset: asset,
      place: place,
      save: save,
      setToBeRemoved: setToBeRemoved,
      isDirty: isDirty,
      cancel: cancel,
      close: close
    };

    function place(asset) {
      current = asset;
    }

    function open(asset) {
      current = asset;
      eventbus.trigger('pedestrianCrossing:selected');
    }

    function cancel() {
      current.toBeDeleted = false;
      eventbus.trigger('pedestrianCrossing:cancelled');
    }

    function getId() {
      return current && current.id;
    }

    function asset() {
      return current;
    }

    function setToBeRemoved(toBeDeleted) {
      current.toBeDeleted = toBeDeleted;
      eventbus.trigger('pedestrianCrossing:changed');
    }

    function isDirty() {
      return current ? current.toBeDeleted : false;
    }

    function save() {
      if (isDirty()) {
        backend.removePointAsset(current.id)
          .done(function() {
            eventbus.trigger('pedestrianCrossing:saved');
            close();
          })
          .fail(function() {
            eventbus.trigger('asset:updateFailed');
          });
      } else {
        backend.createPointAsset(current);
      }
    }

    function close() {
      current = null;
      eventbus.trigger('pedestrianCrossing:unselected');
    }
  };
})(this);