(function(root) {
  var getKey = function(speedLimit) {
    return speedLimit.id + '-' + speedLimit.roadLinkId;
  };

  root.SelectedSpeedLimit = function(collection) {
    var current = null;

    this.openByLink = function(link) {
      if (current) {
        current.isSelected = false;
      }
      collection.get(getKey(link), function(speedLimit) {
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

    this.getId = function() {
      return current.id;
    };

    this.isLink = function(link) {
      return getKey(link) === getKey(current);
    };
  };
})(this);
