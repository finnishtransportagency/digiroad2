(function(root) {
  var getKey = function(speedLimit) {
    return speedLimit.id + '-' + speedLimit.roadLinkId;
  };

  root.SelectedSpeedLimit = function(collection) {
    var current = null;

    this.open = function(id) {
      if (current) {
        current.isSelected = false;
      }
      collection.get(id, function(speedLimit) {
        current = speedLimit;
        current.isSelected = true;
        eventbus.trigger('speedLimit:selected', current);
      });
    };

    this.close = function() {
      if (current) {
        current.isSelected = false;
        current = null;
        eventbus.trigger('speedLimit:unselected');
      }
    };

    this.exists = function() {
      return current !== null;
    };

    this.getKey = function() {
      return getKey(current);
    };

    this.getId = function() {
      return current.id;
    };
  };
})(this);
