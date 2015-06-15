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

    var saveSplit = function() {
      collection.saveSplit(function(newId) {
        selection[0].id = newId;
        originalSpeedLimit = self.getValue();
        collection.setSelection(self);
        dirty = false;
      });
    };

    var saveUnknown = function() {
      // TODO: Save unknown speed limit to backend
      console.log('Saving speed limit to backend');
      dirty = false;
      originalSpeedLimit = selection[0].value;
      eventbus.trigger('speedLimit:saved');
    };

    var saveExisting = function() {
      backend.updateSpeedLimit(selection[0].id, selection[0].value, function(speedLimit) {
        dirty = false;
        selection = [_.merge({}, selection[0], speedLimit)];
        originalSpeedLimit = selection[0].value;
        eventbus.trigger('speedLimit:saved');
      }, function() {
        eventbus.trigger('asset:updateFailed');
      });
    };

    this.isUnknown = function() {
      return !_.has(selection[0], 'id');
    };

    this.isSplit = function() {
      return selection[0].id === null;
    };

    this.save = function() {
      if (self.isUnknown()) {
        saveUnknown();
      } else if (self.isSplit()) {
        saveSplit();
      } else {
        saveExisting();
      }
    };

    var cancelUnknown = function() {
      console.log('cancelling unknown speedlimit'); // TODO
    };

    var cancelSplit = function() {
      eventbus.trigger('speedLimit:unselect', self);
      selection = [];
      dirty = false;
      collection.cancelSplit();
    };

    var cancelExisting = function() {
      selection[0].value = originalSpeedLimit;
      collection.changeValue(selection[0].id, originalSpeedLimit);
      dirty = false;
      eventbus.trigger('speedLimit:cancelled', self);
    };

    this.cancel = function() {
      if (self.isUnknown()) {
        cancelUnknown();
      } else if (self.isSplit()) {
        cancelSplit();
      } else {
        cancelExisting();
      }
    };

    this.exists = function() {
      return !_.isEmpty(selection);
    };

    var getProperty = function(propertyName) {
      return _.has(selection[0], propertyName) ? selection[0][propertyName] : null;
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
        selection = _.map(selection, function(s) { return _.merge({}, s, { value: value }); });
        if (!self.isUnknown()) collection.changeValue(selection[0].id, value);
        dirty = true;
        eventbus.trigger('speedLimit:valueChanged', self);
      }
    };

    this.isDirty = function() {
      return dirty;
    };

    this.isNew = function() {
      return !_.isNumber(self.getId());
    };

    this.isSelected = function(speedLimit) {
      if (self.isUnknown()) {
        return !_.has(speedLimit, 'id') && (selection[0].links[0].mmlId === speedLimit.links[0].mmlId);
      } else if (self.isSplit()) {
        return speedLimit.id === null;
      } else {
        return _.some(selection, { id: speedLimit.id });
      }
    };

    this.selectedFromMap = function(speedLimits) {
      return speedLimits[self.getId()];
    };
  };
})(this);
