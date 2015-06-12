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
      if (_.has(speedLimit, 'id')) {
        selection = [speedLimit];
        collection.fetchSpeedLimit(speedLimit.id, function (fetchedSpeedLimit) {
          selection = [fetchedSpeedLimit];
          originalSpeedLimit = fetchedSpeedLimit.value;
          collection.setSelection(self);
          eventbus.trigger('speedLimit:selected', self);
        });
      } else {
        selection = [collection.getUnknown(speedLimit.links[0].mmlId)];
        collection.setSelection(self);
        eventbus.trigger('speedLimit:selected', self);
      }
    };

    this.openMultiple = function(speedLimits) {
      selection = speedLimits;
    };

    this.close = function() {
      if (!_.isEmpty(selection) && !dirty) {
        eventbus.trigger('speedLimit:unselect', self);
        collection.setSelection(null);
        var id = selection[0].id;
        selection = [];
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

    var getProperty = function(propertyName) {
      return _.has(selection[0], propertyName) && selection[0][propertyName];
    };

    this.getId = function() {
      return getProperty('id');
    };

    this.getEndpoints = function() {
      return getProperty('endpoints');
    };

    this.getValue = function() {
      return getProperty('value');
    };

    this.getModifiedBy = function() {
      return getProperty('modifiedBy');
    };

    this.getModifiedDateTime = function() {
      return getProperty('modifiedDateTime');
    };

    this.getCreatedBy = function() {
      return getProperty('createdBy');
    };

    this.getCreatedDateTime = function() {
      return getProperty('createdDateTime');
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
      return this.getId() === null;
    };

    this.isSelected = function(speedLimit) {
      return _.some(selection, function(selectedSpeedLimit) {
        return (_.has(selectedSpeedLimit, 'id') && selectedSpeedLimit.id === speedLimit.id) ||
               (selectedSpeedLimit.links[0].mmlId === speedLimit.links[0].mmlId);
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
