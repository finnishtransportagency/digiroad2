(function(root) {
  root.SelectedPointAsset = function(collection) {
    var current = null;

    return {
      open: open,
      getId: getId,
      asset: asset,
      setExpired: setExpired,
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
      current.expired = false;
      eventbus.trigger('pedestrianCrossing:cancelled');
    }

    function getId() {
      return current.id;
    }

    function asset() {
      return current;
    }

    function setExpired(expired) {
      current.expired = expired;
      eventbus.trigger('pedestrianCrossing:changed');
    }

    function isDirty() {
      return current.expired;
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