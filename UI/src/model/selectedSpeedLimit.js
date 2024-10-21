(function(root) {
  root.SelectedSpeedLimit = function(backend, collection) {
    var selection = [];
    var self = this;
    var dirty = false;
    var originalSpeedLimitValues = null;
    var isSeparated = false;

    this.splitSpeedLimit = function(id, split) {
      collection.splitSpeedLimit(id, split, function(splitSpeedLimits) {
        selection = [splitSpeedLimits.created, splitSpeedLimits.existing];
        originalSpeedLimitValues = { isSuggested: splitSpeedLimits.existing.value.isSuggested, value: splitSpeedLimits.existing.value.value };
        dirty = true;
        collection.setSelection(self);
        eventbus.trigger('speedLimit:selected', self);
      });
    };

    this.separate = function() {
      selection = collection.separateSpeedLimit(this.getId());
      isSeparated = true;
      dirty = true;
      eventbus.trigger('speedLimits:fetched', collection.getAll());
      eventbus.trigger('speedLimit:separated', self);
      eventbus.trigger('speedLimit:selected', self);
    };

    this.open = function(speedLimit, singleLinkSelect) {
      self.close();
      selection = singleLinkSelect ? [speedLimit] : collection.getGroup(speedLimit);
      originalSpeedLimitValues = { isSuggested: self.getSuggestionValue(), value: self.getValue() };
      collection.setSelection(self);
      eventbus.trigger('speedLimit:selected', self);
    };

    this.openMultiple = function(speedLimits) {
      var partitioned = _.groupBy(speedLimits, isUnknown);
      var existingSpeedLimits = _.uniq(partitioned[false] || [], 'id');
      var unknownSpeedLimits = _.uniq(partitioned[true] || [], 'generatedId');

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
      var partition = _.groupBy(_.map(selection, function(item){ return _.omit(item, 'geometry'); }), isUnknown);
      var unknownSpeedLimits = partition[true];
      var knownSpeedLimits = partition[false];

      var payload = {
        newLimits: _.map(unknownSpeedLimits, function(x) { return _.pick(x, 'linkId', 'startMeasure', 'endMeasure'); }),
        ids: _.map(knownSpeedLimits, 'id'),
        value: { isSuggested: false, value: value }
      };
      backend.updateSpeedLimits(payload, function() {
        dirty = false;
        eventbus.trigger('speedLimits:massUpdateSucceeded', selection.length);
      }, function() {
        eventbus.trigger('speedLimits:massUpdateFailed', selection.length);
      });
    };

    var saveSplit = function() {
      eventbus.trigger('speedLimit:saving');
      collection.saveSplit(function() {
        dirty = false;
        self.close();
      });
    };

    var saveSeparation = function() {
      eventbus.trigger('speedLimit:saving');
      collection.saveSeparation(function() {
        dirty = false;
        isSeparated = false;
        self.close();
      });
    };

    var saveExisting = function() {
      eventbus.trigger('speedLimit:saving');
      var payloadContents = function() {
        if (self.isUnknown()) {
          return { newLimits: _.map(selection, function(s) { return _.pick(s, 'linkId', 'startMeasure', 'endMeasure', 'sideCode'); }) };
        } else {
          return { ids: _.map(selection, 'id') };
        }
      };
      var payload = _.merge({value: {isSuggested: self.getSuggestionValue() ,value:self.getValue()}}, payloadContents());

      backend.updateSpeedLimits(payload, function() {
        dirty = false;
        self.close();
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

    this.getSpeedLimitById = function(id) {
      return collection.getById(id);
    };
    var cancelCreation = function() {
      eventbus.trigger('speedLimit:unselect', self);
      if (isSeparated) {
        var originalSpeedLimit = _.merge({}, selection[0], {value: originalSpeedLimitValues, sideCode: 1});
        collection.replaceSegments([selection[0]], [originalSpeedLimit]);
      }
      collection.setSelection(null);
      selection = [];
      dirty = false;
      isSeparated = false;
      collection.cancelCreation();
    };

    var cancelExisting = function() {
      var newGroup = _.map(selection, function(s) { return _.merge({}, s, { value:  originalSpeedLimitValues }); });
      selection = collection.replaceSegments(selection, newGroup);
      dirty = false;
      eventbus.trigger('speedLimit:cancelled', self);
    };

    var massDeselect = function () {
      selection = [];
      collection.setSelection(null);
      dirty = false;
      eventbus.trigger('speedLimit:massUpdateUnselected');
    };

    this.deselectMultiple = function() {
      massDeselect();
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

    var getBProperty = function(propertyName) {
      return _.has(selection[1], propertyName) ? selection[1][propertyName] : null;
    };


    this.getSpeedLimit = function(id){
      return collection.getByLinkId(id);
    };

    this.getId = function() {
      return getProperty('id');
    };

    this.getValue = function() {
      var speedValue = getProperty('value');
      return !_.isNull(speedValue) && speedValue.value;
    };

    this.getBValue = function() {
      var speedValue = getBProperty('value');
      return !_.isNull(speedValue) && speedValue.value;
    };

    this.getSuggestionValue = function() {
      var speedValue = getProperty('value');
      return !_.isNull(speedValue) && speedValue.isSuggested;
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
        var newGroup = _.map(selection, function(s) { return _.merge({}, s, { value: value }); });
        selection = collection.replaceSegments(selection, newGroup);
        if(!_.isNaN(selection[0].value.value)) {
          dirty = true;
          eventbus.trigger('speedLimit:valueChanged', self);
        }
      }
    };

    this.setMassValue = function(value) {
      var newGroup = _.map(selection, function(s) { return _.assign({}, s, { value: value }); });
      selection = collection.replaceSegments(selection, newGroup);
      eventbus.trigger('speedLimit:valueChanged', self);
    };

    this.setAValue = function(value) {
      if (value != selection[0].value) {
        selection[0].value = value;
        eventbus.trigger('speedLimit:valueChanged', self);
      }
    };

    this.setBValue = function(value) {
      if (value != selection[1].value) {
        selection[1].value = value;
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
      return _.some(selection, function(selectedSpeedLimit) {
        return isEqual(speedLimit, selectedSpeedLimit);
      });
    };

    this.isSeparable = function() {
      return !self.isUnknown() &&
        getProperty('sideCode') === validitydirections.bothDirections &&
        getProperty('trafficDirection') === 'BothDirections' &&
        !self.isSplit() &&
        selection.length === 1;
    };

    this.isSaveable = function() {
      var valuesDiffer = function () { return (selection[0].value !== selection[1].value); };
      if (this.isDirty()) {
        if (this.isSplitOrSeparated() && valuesDiffer()) return true;
        else if (!this.isSplitOrSeparated()) return !_.isUndefined(selection[0].value.value);
      }
      return false;
    };

    var isEqual = function(a, b) {
      return (_.has(a, 'generatedId') && _.has(b, 'generatedId') && (a.generatedId === b.generatedId)) ||
        ((!isUnknown(a) && !isUnknown(b)) && (a.id === b.id));
    };

    this.isSuggested = function() {
      var  suggestedProp = getProperty('value');
      return !_.isNull(suggestedProp) && suggestedProp.isSuggested;
    };
  };
})(this);
