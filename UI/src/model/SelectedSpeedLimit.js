(function(root) {
  root.SelectedSpeedLimit = function(backend, collection) {
    var selection = [];
    var self = this;
    var dirty = false;
    var originalSpeedLimit = null;

    eventbus.on('speedLimit:split', function() {
      collection.fetchSpeedLimit(null, function(speedLimit) {
        selection = [speedLimit];
        originalSpeedLimit = speedLimit.value;
        dirty = true;
        eventbus.trigger('speedLimit:selected', self);
      });
    });

    this.open = function(speedLimit) {
      self.close();
      selection = [speedLimit];
      collection.fetchSpeedLimit(speedLimit.id, function(fetchedSpeedLimit) {
        selection = [fetchedSpeedLimit];
        originalSpeedLimit = fetchedSpeedLimit.value;
        collection.setSelection(self);
        eventbus.trigger('speedLimit:selected', self);
      });
    };

    this.openMultiple = function(speedLimits) {
      selection = speedLimits;
    };

    this.close = function() {
      if (!_.isEmpty(selection) && !dirty) {
        collection.setSelection(null);
        var id = selection[0].id;
        selection = [];
        eventbus.trigger('speedLimit:unselected', id);
      }
    };

    this.closeMultiple = function() {
      selection = [];
    };

    this.saveSplit = function() {
      collection.saveSplit();
    };

    this.cancelSplit = function() {
      var id = selection[0].id;
      selection = [];
      dirty = false;
      collection.cancelSplit();
      eventbus.trigger('speedLimit:unselected', id);
    };

    this.save = function() {
      backend.updateSpeedLimit(selection[0].id, selection[0].value, function(speedLimit) {
        dirty = false;
        selection = [_.merge({}, selection[0], speedLimit)];
        originalSpeedLimit = selection[0].value;
        eventbus.trigger('speedLimit:saved', selection[0]);
      }, function() {
        eventbus.trigger('asset:updateFailed');
      });
    };

    this.cancel = function() {
      selection[0].value = originalSpeedLimit;
      collection.changeValue(selection[0].id, originalSpeedLimit);
      dirty = false;
      eventbus.trigger('speedLimit:cancelled', self);
    };

    this.exists = function() {
      return !_.isEmpty(selection);
    };

    this.getId = function() {
      return selection[0].id;
    };

    this.getEndpoints = function() {
      return selection[0].endpoints;
    };

    this.getValue = function() {
      return selection[0].value;
    };

    this.getModifiedBy = function() {
      return selection[0].modifiedBy;
    };

    this.getModifiedDateTime = function() {
      return selection[0].modifiedDateTime;
    };

    this.getCreatedBy = function() {
      return selection[0].createdBy;
    };

    this.getCreatedDateTime = function() {
      return selection[0].createdDateTime;
    };

    this.get = function() {
      return selection[0];
    };

    this.setValue = function(value) {
      if (value != selection[0].value) {
        collection.changeValue(selection[0].id, value);
        selection[0].value = value;
        dirty = true;
        eventbus.trigger('speedLimit:valueChanged', self);
      }
    };

    this.isDirty = function() {
      return dirty;
    };

    this.isNew = function() {
      return selection[0].id === null;
    };

    this.isSelected = function(speedLimit) {
      return _.some(selection, function(selectedSpeedLimit) {
        return selectedSpeedLimit.id === speedLimit.id;
      });
    };

    this.selectedFromMap = function(speedLimits) {
      return speedLimits[self.getId()];
    };

    eventbus.on('speedLimit:saved', function(speedLimit) {
      selection = [speedLimit];
      originalSpeedLimit = speedLimit.value;
      collection.setSelection(self);
      dirty = false;
    });
  };
})(this);
