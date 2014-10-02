(function(root) {
  root.SelectedSpeedLimit = function(backend, collection) {
    var current = null;
    var self = this;
    var dirty = false;
    var originalSpeedLimit = null;

    eventbus.on('speedLimit:split', function() {
      collection.fetchSpeedLimit(null, function(speedLimit) {
        current = speedLimit;
        originalSpeedLimit = speedLimit.limit;
        dirty = true;
        eventbus.trigger('speedLimit:selected', self);
      });
    });

    this.open = function(id) {
      self.close();
      collection.fetchSpeedLimit(id, function(speedLimit) {
        current = speedLimit;
        originalSpeedLimit = speedLimit.limit;
        collection.markAsSelected(speedLimit.id);
        eventbus.trigger('speedLimit:selected', self);
      });
    };

    this.close = function() {
      if (current && !dirty) {
        collection.markAsDeselected(current.id);
        var id = current.id;
        current = null;
        eventbus.trigger('speedLimit:unselected', id);
      }
    };

    this.saveSplit = function() {
      collection.saveSplit();
    };

    this.cancelSplit = function() {
      var id = current.id;
      current = null;
      dirty = false;
      collection.cancelSplit();
      eventbus.trigger('speedLimit:unselected', id);
    };

    this.save = function() {
      backend.updateSpeedLimit(current.id, current.limit, function(speedLimit) {
        dirty = false;
        current = _.merge({}, current, speedLimit);
        originalSpeedLimit = current.limit;
        eventbus.trigger('speedLimit:saved', current);
      }, function() {
        eventbus.trigger('asset:updateFailed');
      });
    };

    this.cancel = function() {
      current.limit = originalSpeedLimit;
      collection.changeLimit(current.id, originalSpeedLimit);
      dirty = false;
      eventbus.trigger('speedLimit:cancelled', self);
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

    this.getModifiedBy = function() {
      return current.modifiedBy;
    };

    this.getModifiedDateTime = function() {
      return current.modifiedDateTime;
    };

    this.getCreatedBy = function() {
      return current.createdBy;
    };

    this.getCreatedDateTime = function() {
      return current.createdDateTime;
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

    this.isNew = function() {
      return current.id === null;
    };

    this.isValidInBothDirections = function() {
      return current.sideCode == 1;
    };

    eventbus.on('speedLimit:saved', function(speedLimit) {
      current = speedLimit;
      originalSpeedLimit = speedLimit.limit;
      collection.markAsSelected(speedLimit.id);
      dirty = false;
    });
  };
})(this);
