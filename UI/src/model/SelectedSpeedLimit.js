(function(root) {
  root.SelectedSpeedLimit = function(collection) {
    var current = null;

    this.openByLink = function(link) {
      if (current) {
        current.isSelected = false;
      }
      collection.getByLink(link, function(speedLimit) {
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
  };
})(this);
