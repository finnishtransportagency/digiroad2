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
      cancel: cancel
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
      return current.id;
    }

    function asset() {
      return current;
    }

    function setToBeRemoved(expired) {
      current.expired = expired;
      eventbus.trigger('pedestrianCrossing:changed');
    }

    function isDirty() {
      return current.toBeDeleted;
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