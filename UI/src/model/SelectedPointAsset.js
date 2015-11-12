(function(root) {
  root.SelectedPointAsset = function() {
    var current = null;

    return {
      open: open,
      getId: getId,
      asset: asset,
      setExpired: setExpired,
      place: place
    };

    function place(asset) {
      current = asset;
    }

    function open(asset) {
      current = asset;
      eventbus.trigger('pedestrianCrossing:selected');
    }

    function getId() {
      return current.id;
    }

    function asset() {
      return current;
    }

    function setExpired(expired) {
      current.expired = true;
      eventbus.trigger('pedestrianCrossing:changed');
    }
  };
})(this);