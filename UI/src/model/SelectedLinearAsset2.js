(function(root) {
  root.SelectedLinearAsset2 = function(backend, collection, typeId, singleElementEventCategory, multiElementEventCategory, isSeparableAssetType) {
    var selection = [];
    var self = this;
    var dirty = false;
    var originalSpeedLimitValue = null;
    var isSeparated = false;

    var singleElementEvent = function(eventName) {
      return singleElementEventCategory + ':' + eventName;
    };

    var multiElementEvent = function(eventName) {
      return multiElementEventCategory + ':' + eventName;
    };

    this.splitSpeedLimit = function(id, split) {
      collection.splitSpeedLimit(id, split, function(splitSpeedLimits) {
        selection = [splitSpeedLimits.created, splitSpeedLimits.existing];
        originalSpeedLimitValue = splitSpeedLimits.existing.value;
        dirty = true;
        collection.setSelection(self);
        eventbus.trigger(singleElementEvent('selected'), self);
      });
    };

    this.separate = function() {
      selection = collection.separateSpeedLimit(this.getId());
      isSeparated = true;
      dirty = true;
      eventbus.trigger(multiElementEvent('fetched'), collection.getAll());
      eventbus.trigger(singleElementEvent('separated'), self);
      eventbus.trigger(singleElementEvent('selected'), self);
    };

    this.open = function(speedLimit, singleLinkSelect) {
      self.close();
      selection = singleLinkSelect ? [speedLimit] : collection.getGroup(speedLimit);
      originalSpeedLimitValue = self.getValue();
      collection.setSelection(self);
      eventbus.trigger(singleElementEvent('selected'), self);
    };

    this.openMultiple = function(speedLimits) {
      var partitioned = _.groupBy(speedLimits, isUnknown);
      var existingSpeedLimits = _.unique(partitioned[false] || [], 'id');
      var unknownSpeedLimits = _.unique(partitioned[true] || [], 'generatedId');

      selection = existingSpeedLimits.concat(unknownSpeedLimits);
    };

    this.close = function() {
      if (!_.isEmpty(selection) && !dirty) {
        eventbus.trigger(singleElementEvent('unselect'), self);
        collection.setSelection(null);
        selection = [];
      }
    };

    this.closeMultiple = function() {
      selection = [];
    };

    this.saveMultiple = function(value) {
      eventbus.trigger(singleElementEvent('saving'));
      var partition = _.groupBy(selection, isUnknown);
      var unknownSpeedLimits = partition[true];
      var knownSpeedLimits = partition[false];

      var payload = {
        newLimits: _.map(unknownSpeedLimits, function(x) { return _.pick(x, 'mmlId', 'startMeasure', 'endMeasure'); }),
        ids: _.pluck(knownSpeedLimits, 'id'),
        value: value
      };
      backend.updateSpeedLimits(payload, function() {
        eventbus.trigger(multiElementEvent('massUpdateSucceeded'), selection.length);
      }, function() {
        eventbus.trigger(multiElementEvent('massUpdateFailed'), selection.length);
      });
    };

    var saveSplit = function() {
      eventbus.trigger(singleElementEvent('saving'));
      collection.saveSplit(function() {
        dirty = false;
        self.close();
      });
    };

    var saveSeparation = function() {
      eventbus.trigger(singleElementEvent('saving'));
      collection.saveSeparation(function() {
        dirty = false;
        isSeparated = false;
        self.close();
      });
    };

    var saveExisting = function() {
      eventbus.trigger(singleElementEvent('saving'));
      var payloadContents = function() {
        if (self.isUnknown()) {
          return { newLimits: _.map(selection, function(s) { return _.pick(s, 'mmlId', 'startMeasure', 'endMeasure'); }) };
        } else {
          return { ids: _.pluck(selection, 'id') };
        }
      };
      var payload = _.merge({value: self.getValue(), typeId: typeId}, payloadContents());
      var backendOperation = _.isUndefined(self.getValue()) ? backend.deleteLinearAssets : backend.updateLinearAssets;

      backendOperation(payload, function() {
        dirty = false;
        self.close();
        eventbus.trigger(singleElementEvent('saved'));
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
      return !isSeparated && selection[0].id === null;
    };

    this.isSeparated = function() {
      return isSeparated;
    };

    this.isSplitOrSeparated = function() {
      return this.isSplit() || this.isSeparated();
    };

    this.save = function() {
      if (self.isSplit()) {
        saveSplit();
      } else if (isSeparated) {
        saveSeparation();
      } else {
        saveExisting();
      }
    };

    var cancelCreation = function() {
      eventbus.trigger(singleElementEvent('unselect'), self);
      if (isSeparated) {
        var originalSpeedLimit = _.merge({}, selection[0], {value: originalSpeedLimitValue, sideCode: 1});
        collection.replaceSegments([selection[0]], [originalSpeedLimit]);
      }
      collection.setSelection(null);
      selection = [];
      dirty = false;
      isSeparated = false;
      collection.cancelCreation();
    };

    var cancelExisting = function() {
      var newGroup = _.map(selection, function(s) { return _.assign({}, s, { value: originalSpeedLimitValue }); });
      selection = collection.replaceSegments(selection, newGroup);
      dirty = false;
      eventbus.trigger(singleElementEvent('cancelled'), self);
    };

    this.cancel = function() {
      if (self.isSplit() || self.isSeparated()) {
        cancelCreation();
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
      var value = getProperty('value');
      return _.isNull(value) ? undefined : value;
    };

    this.getModifiedBy = function() {
      return dateutil.extractLatestModifications(selection, 'modifiedAt').modifiedBy;
    };

    this.getModifiedDateTime = function() {
      return dateutil.extractLatestModifications(selection, 'modifiedAt').modifiedAt;
    };

    this.getCreatedBy = function() {
      return selection.length === 1 ? getProperty('createdBy') : null;
    };

    this.getCreatedDateTime = function() {
      return selection.length === 1 ? getProperty('createdAt') : null;
    };

    this.get = function() {
      return selection;
    };

    this.count = function() {
      return selection.length;
    };

    this.setValue = function(value) {
      if (value != selection[0].value) {
        var newGroup = _.map(selection, function(s) { return _.assign({}, s, { value: value }); });
        selection = collection.replaceSegments(selection, newGroup);
        dirty = true;
        eventbus.trigger(singleElementEvent('valueChanged'), self);
      }
    };

    this.removeValue = function() {
      this.setValue(undefined);
    };

    this.setAValue = function(value) {
      if (value != selection[0].value) {
        selection[0].value = value;
        eventbus.trigger(singleElementEvent('valueChanged'), self);
      }
    };

    this.setBValue = function(value) {
      if (value != selection[1].value) {
        selection[1].value = value;
        eventbus.trigger(singleElementEvent('valueChanged'), self);
      }
    };

    this.isDirty = function() {
      return dirty;
    };

    this.isNew = function() {
      return !_.isNumber(self.getId());
    };

    this.isSelected = function(speedLimit) {
      return _.some(selection, function(selectedSpeedLimit) {
        return isEqual(speedLimit, selectedSpeedLimit);
      });
    };

    this.isSeparable = function() {
      return isSeparableAssetType &&
        !self.isUnknown() &&
        getProperty('sideCode') === validitydirections.bothDirections &&
        getProperty('trafficDirection') === 'BothDirections' &&
        !self.isSplit() &&
        selection.length === 1;
    };

    this.isSaveable = function() {
      var valuesDiffer = function () { return (selection[0].value !== selection[1].value); };
      if (this.isDirty()) {
        if (this.isSplitOrSeparated() && valuesDiffer()) return true;
        else if (!this.isSplitOrSeparated()) return true;
      }
      return false;
    };

    var isEqual = function(a, b) {
      return (_.has(a, 'generatedId') && _.has(b, 'generatedId') && (a.generatedId === b.generatedId)) ||
        ((!isUnknown(a) && !isUnknown(b)) && (a.id === b.id));
    };
  };
})(this);
