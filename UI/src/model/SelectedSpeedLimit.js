(function(root) {
  root.SelectedSpeedLimit = function(collection) {
    var current = null;
    var self = this;

    this.openByLink = function(link) {
      self.close();
      collection.getByLink(link, function(speedLimit) {
        current = speedLimit;
        current.isSelected = true;
        eventbus.trigger('speedLimit:selected', self);
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

    this.getEndpoints = function() {
      return current.endpoints;
    };

    this.getLimit = function() {
      return current.limit;
    };

    this.getSideCode = function() {
      return current.sideCode;
    };

    this.setLimit = function(limit) {
      current.limit = limit;
      eventbus.trigger('speedLimit:limitChanged', self);
    };
  };
})(this);
