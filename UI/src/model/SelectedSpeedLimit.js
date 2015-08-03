(function(root) {
  root.SelectedSpeedLimit = function(backend, collection, roadCollection) {
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

    this.open = function(speedLimit, singleLinkSelect) {
      self.close();
      selection = singleLinkSelect ? [speedLimit] : collection.getGroup(speedLimit);
      originalSpeedLimit = self.getValue();
      collection.setSelection(self);
      eventbus.trigger('speedLimit:selected', self);
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
      eventbus.trigger('speedLimit:saving');
      var partition = _.groupBy(selection, isUnknown);
      var unknownSpeedLimits = partition[true];
      var knownSpeedLimits = partition[false];

      var payload = {
        newLimits: _.map(unknownSpeedLimits, function(x) { return _.pick(x, 'mmlId', 'startMeasure', 'endMeasure'); }),
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
      eventbus.trigger('speedLimit:saving');
      collection.saveSplit(function(speedLimit) {
        selection = [_.merge({}, selection[0], speedLimit)];
        originalSpeedLimit = self.getValue();
        collection.setSelection(self);
        dirty = false;
      });
    };

    var saveExisting = function() {
      eventbus.trigger('speedLimit:saving');
      var payloadContents = function() {
        if (self.isUnknown()) {
          return { newLimits: _.map(selection, function(s) { return _.pick(s, 'mmlId', 'startMeasure', 'endMeasure'); }) };
        } else {
          return { ids: _.pluck(selection, 'id') };
        }
      };
      var payload = _.merge({value: self.getValue()}, payloadContents());

      backend.updateSpeedLimits(payload, function(speedLimits) {
        selection = collection.replaceSegments(selection, speedLimits);
        originalSpeedLimit = self.getValue();
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

    var cancelSplit = function() {
      eventbus.trigger('speedLimit:unselect', self);
      collection.setSelection(null);
      selection = [];
      dirty = false;
      collection.cancelSplit();
    };

    var cancelExisting = function() {
      var newGroup = _.map(selection, function(s) { return _.merge({}, s, { value: originalSpeedLimit }); });
      selection = collection.replaceSegments(selection, newGroup);
      dirty = false;
      eventbus.trigger('speedLimit:cancelled', self);
    };

    this.cancel = function() {
      if (self.isSplit()) {
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

    this.getValue = function() {
      return getProperty('value');
    };

    var segmentWithLatestModifications = function() {
      return _.last(_.sortBy(selection, function(s) {
        return moment(s.modifiedDateTime, "DD.MM.YYYY HH:mm:ss").valueOf() || 0;
      }));
    };

    this.getModifiedBy = function() {
      return segmentWithLatestModifications().modifiedBy;
    };

    this.getModifiedDateTime = function() {
      return segmentWithLatestModifications().modifiedDateTime;
    };

    this.getCreatedBy = function() {
      return selection.length === 1 ? getProperty('createdBy') : null;
    };

    this.getCreatedDateTime = function() {
      return selection.length === 1 ? getProperty('createdDateTime') : null;
    };

    this.get = function() {
      return selection;
    };

    this.count = function() {
      return selection.length;
    };

    this.setValue = function(value) {
      if (value != selection[0].value) {
        var newGroup = _.map(selection, function(s) { return _.merge({}, s, { value: value }); });
        selection = collection.replaceSegments(selection, newGroup);
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

    this.isSeparable = function() {
      return getProperty('sideCode') === validitydirections.bothDirections &&
        roadCollection.get(getProperty('mmlId')).getData().trafficDirection === 'BothDirections' &&
        selection.length === 1;
    };

    var isEqual = function(a, b) {
      return (_.has(a, 'generatedId') && _.has(b, 'generatedId') && (a.generatedId === b.generatedId)) ||
        ((!isUnknown(a) && !isUnknown(b)) && (a.id === b.id));
    };
  };
})(this);
