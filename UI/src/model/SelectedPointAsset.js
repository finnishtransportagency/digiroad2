(function(root) {
  root.SelectedPointAsset = function(collection) {
    var current = null;

    return {
      open: open,
      getId: getId,
      asset: asset,
      setToBeDeleted: setToBeDeleted,
      place: place,
      save: save,
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

    function setToBeDeleted(deleted) {
      current.toBeDeleted = deleted;
      eventbus.trigger('pedestrianCrossing:changed');
    }

    function isDirty() {
      return current.toBeDeleted;
    }

    function save() {
      collection.save(current)
        .done(function() {
          eventbus.trigger('pedestrianCrossing:saved');
          close();
        })
        .fail(function() {
          eventbus.trigger('asset:updateFailed');
        });
    }

    function close() {
      current = null;
      eventbus.trigger('pedestrianCrossing:unselected');
    }
  };
})(this);