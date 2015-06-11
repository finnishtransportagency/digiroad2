(function(root) {
  root.SelectedSpeedLimit = function(backend, collection) {
    var current = null;
    var self = this;
    var dirty = false;
    var originalSpeedLimit = null;

    eventbus.on('speedLimit:split', function() {
      collection.fetchSpeedLimit(null, function(speedLimit) {
        current = speedLimit;
        originalSpeedLimit = speedLimit.value;
        dirty = true;
        eventbus.trigger('speedLimit:selected', self);
      });
    });

    this.open = function(id) {
      self.close();
      collection.fetchSpeedLimit(id, function(speedLimit) {
        current = speedLimit;
        originalSpeedLimit = speedLimit.value;
        collection.setSelection(self);
        eventbus.trigger('speedLimit:selected', self);
      });
    };

    this.close = function() {
      if (current && !dirty) {
        collection.setSelection(null);
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
      backend.updateSpeedLimit(current.id, current.value, function(speedLimit) {
        dirty = false;
        current = _.merge({}, current, speedLimit);
        originalSpeedLimit = current.value;
        eventbus.trigger('speedLimit:saved', current);
      }, function() {
        eventbus.trigger('asset:updateFailed');
      });
    };

    this.cancel = function() {
      current.value = originalSpeedLimit;
      collection.changeValue(current.id, originalSpeedLimit);
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

    this.getValue = function() {
      return current.value;
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

    this.setValue = function(value) {
      if (value != current.value) {
        collection.changeValue(current.id, value);
        current.value = value;
        dirty = true;
        eventbus.trigger('speedLimit:valueChanged', self);
      }
    };

    this.isDirty = function() {
      return dirty;
    };

    this.isNew = function() {
      return current.id === null;
    };

    this.selectedFromMap = function(speedLimits) {
      return speedLimits[self.getId()];
    };

    eventbus.on('speedLimit:saved', function(speedLimit) {
      current = speedLimit;
      originalSpeedLimit = speedLimit.value;
      collection.setSelection(self);
      dirty = false;
    });
  };
})(this);
