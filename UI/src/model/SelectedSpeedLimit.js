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
        selection = collection.getGroupByGeneratedId(speedLimit.generatedId);
        originalSpeedLimit = self.getValue();
        collection.setSelection(self);
        eventbus.trigger('speedLimit:selected', self);
      } else {
        selection = collection.getGroupBySegmentId(speedLimit.id);
        // TODO: Fetch details of all links. Fail if group has different links
        collection.fetchSpeedLimit(speedLimit.id, function(fetchedSpeedLimit) {
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
      collection.saveSplit(function(speedLimit) {
        selection = [_.merge({}, selection[0], speedLimit)];
        originalSpeedLimit = self.getValue();
        collection.setSelection(self);
        dirty = false;
      });
    };

    var saveExisting = function() {
      var payloadContents = function() {
        if (self.isUnknown()) {
          return { newLimits: _.map(selection, function(s) { return _.pick(s, 'mmlId', 'startMeasure', 'endMeasure'); }) };
        } else {
          return { ids: _.pluck(selection, 'id') };
        }
      };
      var payload = _.merge({value: self.getValue()}, payloadContents());

      backend.updateSpeedLimits(payload, function() {
        dirty = false;
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
      if (self.isSplit()) {
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
      selection = _.map(selection, function(s) { return _.merge({}, s, { value: originalSpeedLimit }); });
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
      return selection;
    };

    this.count = function() {
      return selection.length;
    };

    this.setValue = function(value) {
      if (value != selection[0].value) {
        selection = _.map(selection, function(s) { return _.merge({}, s, { value: value }); });
        collection.updateFromSelection(self.isUnknown());
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
