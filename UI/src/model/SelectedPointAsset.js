(function(root) {
  root.SelectedPointAsset = function(backend, collection) {
    var current = null;
    var dirty = false;
    var originalAsset;
    return {
      open: open,
      getId: getId,
      asset: asset,
      place: place,
      move: move,
      save: save,
      setToBeRemoved: setToBeRemoved,
      isDirty: isDirty,
      isNew: isNew,
      cancel: cancel,
      close: close,
      exists: exists,
      isSelected: isSelected
    };

    function place(asset) {
      dirty = true;
      current = asset;
      eventbus.trigger('pedestrianCrossing:selected');
    }

    function move(asset) {
      dirty = true;
      current = asset;
      eventbus.trigger('pedestrianCrossing:changed');
    }

    function open(asset) {
      originalAsset = asset;
      current = asset;
      eventbus.trigger('pedestrianCrossing:selected');
    }

    function cancel() {
      dirty = false;
      current = originalAsset;
      eventbus.trigger('pedestrianCrossing:cancelled');
    }

    function getId() {
      return current && current.id;
    }

    function asset() {
      return current;
    }

    function exists() {
      return !_.isNull(current);
    }

    function setToBeRemoved(toBeDeleted) {
      dirty = true;
      current.toBeDeleted = toBeDeleted;
      eventbus.trigger('pedestrianCrossing:changed');
    }

    function isDirty() {
      return dirty;
    }

    function isNew() {
      return getId() === 0;
    }

    function save() {
      if (current.toBeDeleted) {
        backend.removePointAsset(current.id).done(done).fail(fail);
      } else if (isNew()) {
        backend.createPointAsset(current).done(done).fail(fail);
      } else {
        backend.updatePointAsset(current).done(done).fail(fail);
      }

      function done() {
        eventbus.trigger('pedestrianCrossing:saved');
        close();
      }

      function fail() {
        eventbus.trigger('asset:updateFailed');
      }
    }

    function close() {
      current = null;
      dirty = false;
      eventbus.trigger('pedestrianCrossing:unselected');
    }

    function isSelected(asset) {
      return getId() === asset.id;
    }
  };
})(this);
