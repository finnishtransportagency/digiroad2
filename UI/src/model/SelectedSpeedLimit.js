(function(root) {
  root.SelectedSpeedLimit = function(backend, collection) {
    var selection = [];
    var self = this;
    var dirty = false;
    var originalSpeedLimit = null;

    this.splitSpeedLimit = function(id, mmlId, split) {
      collection.splitSpeedLimit(id, mmlId, split, function(createdSpeedLimit) {
        selection = [createdSpeedLimit];
        originalSpeedLimit = createdSpeedLimit.value;
        dirty = true;
        collection.setSelection(self);
        eventbus.trigger('speedLimit:selected', self);
      });
    };

    this.open = function(speedLimit) {
      self.close();
      if (isUnknown(speedLimit)) {
        selection = [collection.getUnknown(speedLimit.generatedId)];
        originalSpeedLimit = self.getValue();
        collection.setSelection(self);
        eventbus.trigger('speedLimit:selected', self);
      } else {
        selection = [speedLimit];
        collection.fetchSpeedLimit(speedLimit.id, function(fetchedSpeedLimit) {
          selection = [fetchedSpeedLimit];
          originalSpeedLimit = fetchedSpeedLimit.value;
          collection.setSelection(self);
          eventbus.trigger('speedLimit:selected', self);
        });
      }
    };

    this.openMultiple = function(speedLimits) {
      var partitioned = _.groupBy(speedLimits, isUnknown);
      var existingSpeedLimits = _.unique(partitioned[false] || [], 'id');
      var unknownSpeedLimits = _.unique(partitioned[true] || [], 'generatedId');

      selection = existingSpeedLimits.concat(unknownSpeedLimits);
    };

    this.close = function() {
      if (!_.isEmpty(selection) && !dirty) {
        eventbus.trigger('speedLimit:unselect', self);
        collection.setSelection(null);
        selection = [];
      }
    };

    this.closeMultiple = function() {
      selection = [];
    };

    this.saveMultiple = function(value) {
      var partition = _.groupBy(selection, isUnknown);
      var unknownSpeedLimits = partition[true];
      var knownSpeedLimits = partition[false];

      var payload = {
        newLimits: _.map(unknownSpeedLimits, function(x) { return _.pick(x.links[0], 'mmlId', 'startMeasure', 'endMeasure'); }),
        ids: _.pluck(knownSpeedLimits, 'id'),
        value: value
      };
      backend.updateSpeedLimits(payload, function() {
        eventbus.trigger('speedLimits:massUpdateSucceeded', selection.length);
      }, function() {
        eventbus.trigger('speedLimits:massUpdateFailed', selection.length);
      });
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
      var link = self.get().links[0];
      var singleLinkSpeedLimit = {
        mmlId: link.mmlId,
        startMeasure: link.startMeasure,
        endMeasure: link.endMeasure,
        value: self.getValue()
      };
      backend.createSingleLinkSpeedLimit(singleLinkSpeedLimit, function(speedLimit) {
        dirty = false;
        selection = [_.merge({}, selection[0], speedLimit)];
        originalSpeedLimit = selection[0].value;
        collection.createSpeedLimitForUnknown(self.get());
        eventbus.trigger('speedLimit:saved');
      }, function() {
        eventbus.trigger('asset:updateFailed');
      });
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

    var isUnknown = function(speedLimit) {
      return !_.has(speedLimit, 'id');
    };

    this.isUnknown = function() {
      return isUnknown(selection[0]);
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
      selection[0].value = originalSpeedLimit;
      dirty = false;
      eventbus.trigger('speedLimit:cancelled', self);
    };

    var cancelSplit = function() {
      eventbus.trigger('speedLimit:unselect', self);
      collection.setSelection(null);
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

    this.count = function() {
      return selection.length;
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
      if (self.isSplit()) {
        return speedLimit.id === null;
      } else {
        return _.some(selection, function(selectedSpeedLimit) {
          return isEqual(speedLimit, selectedSpeedLimit);
        });
      }
    };

    var isEqual = function(a, b) {
      return (_.has(a, 'generatedId') && _.has(b, 'generatedId') && (a.generatedId === b.generatedId)) ||
        ((!isUnknown(a) && !isUnknown(b)) && (a.id === b.id));
    };
  };
})(this);
