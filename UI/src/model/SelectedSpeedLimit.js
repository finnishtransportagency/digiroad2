(function(root) {
  root.SelectedSpeedLimit = function(backend, collection) {
    var selection = [];
    var self = this;
    var dirty = false;
    var originalSpeedLimit = null;

    this.splitSpeedLimit = function(id, mmlId, split) {
      throw "Splitting it not yet supported on speed limit chains";
/*      collection.splitSpeedLimit(id, mmlId, split, function(createdSpeedLimit) {
        selection = [createdSpeedLimit];
        originalSpeedLimit = createdSpeedLimit.value;
        dirty = true;
        collection.setSelection(self);
        eventbus.trigger('speedLimit:selected', self);
      });*/
    };

    var enrichWithModificationData = function(collection, speedLimits) {
      var speedLimitsById = _.groupBy(speedLimits, 'id');
      return _.map(collection, function(s) {
        return _.merge({}, s, _.pick(speedLimitsById[s.id][0], 'modifiedBy', 'modifiedDateTime'));
      });
    };

    this.open = function(speedLimit) {
      self.close();
      selection = collection.getGroup(speedLimit);
      if (isUnknown(speedLimit)) {
        originalSpeedLimit = self.getValue();
        collection.setSelection(self);
        eventbus.trigger('speedLimit:selected', self);
      } else {
        var ids = _.pluck(selection, 'id');
        backend.getSpeedLimitDetails(ids, function(speedLimits) {
          selection = enrichWithModificationData(selection, speedLimits);
          originalSpeedLimit = speedLimits[0].value;
          collection.setSelection(self);
          eventbus.trigger('speedLimit:selected', self);
        });
      }
    };

    this.openMultiple = function(speedLimits) {
      throw "Multiselect is not yet supported on speed limit chains";
/*      var partitioned = _.groupBy(speedLimits, isUnknown);
      var existingSpeedLimits = _.unique(partitioned[false] || [], 'id');
      var unknownSpeedLimits = _.unique(partitioned[true] || [], 'generatedId');

      selection = existingSpeedLimits.concat(unknownSpeedLimits);*/
    };

    this.close = function() {
      if (!_.isEmpty(selection) && !dirty) {
        eventbus.trigger('speedLimit:unselect', self);
        collection.setSelection(null);
        selection = [];
      }
    };

    this.closeMultiple = function() {
      throw "Multiselect is not yet supported on speed limit chains";
//      selection = [];
    };

    this.saveMultiple = function(value) {
      throw "Multiselect is not yet supported on speed limit chains";
/*      var partition = _.groupBy(selection, isUnknown);
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
      });*/
    };

    var saveSplit = function() {
      throw "Split is not yet supported on speed limit chains";
/*      collection.saveSplit(function(speedLimit) {
        selection = [_.merge({}, selection[0], speedLimit)];
        originalSpeedLimit = self.getValue();
        collection.setSelection(self);
        dirty = false;
      });*/
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

      backend.updateSpeedLimits(payload, function(speedLimits) {
        var speedLimitGroup = _.flatten(_.pluck(speedLimits, 'speedLimitLinks'));
        selection = collection.replaceGroup(selection[0], speedLimitGroup);
        selection = enrichWithModificationData(selection, speedLimits);
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
      throw "Split is not yet supported on speed limit chains";
/*      eventbus.trigger('speedLimit:unselect', self);
      collection.setSelection(null);
      selection = [];
      dirty = false;
      collection.cancelSplit();*/
    };

    var cancelExisting = function() {
      var newGroup = _.map(selection, function(s) { return _.merge({}, s, { value: originalSpeedLimit }); });
      selection = collection.replaceGroup(selection[0], newGroup);
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

    this.getEndpoints = function() {
      return getProperty('endpoints');
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
        var newGroup = _.map(selection, function(s) { return _.merge({}, s, { value: value }); });
        selection = collection.replaceGroup(selection[0], newGroup);
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
