(function(root) {
  root.SelectedSpeedLimit = function(collection) {
    var current = null;
    var self = this;
    var dirty = false;

    this.open = function(id) {
      self.close();
      collection.fetchSpeedLimit(id, function(speedLimit) {
        current = speedLimit;
        collection.markAsSelected(speedLimit.id);
        eventbus.trigger('speedLimit:selected', self);
      });
    };

    this.close = function() {
      if (current && !dirty) {
        collection.markAsDeselected(current.id);
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

    this.get = function() {
      return current;
    };

    this.setLimit = function(limit) {
      if (limit != current.limit) {
        collection.changeLimit(current.id, limit);
        current.limit = limit;
        dirty = true;
        eventbus.trigger('speedLimit:limitChanged', self);
      }
    };

    this.isDirty = function() {
      return dirty;
    };
  };
})(this);
