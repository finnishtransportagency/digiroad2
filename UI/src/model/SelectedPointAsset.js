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
        backend.removePointAsset(current.id)
          .done(function() {
            eventbus.trigger('pedestrianCrossing:saved');
            close();
          })
          .fail(function() {
            eventbus.trigger('asset:updateFailed');
          });
      } else if (isNew()) {
        backend.createPointAsset(current)
          .done(function() {
            eventbus.trigger('pedestrianCrossing:saved');
            close();
          })
          .fail(function() {
            eventbus.trigger('asset:creationFailed');
          });
      } else {
        backend.updatePointAsset(current)
          .done(function() {
            eventbus.trigger('pedestrianCrossing:saved');
            close();
          })
          .fail(function() {
            eventbus.trigger('asset:updateFailed');
          });
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
