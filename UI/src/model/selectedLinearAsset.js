(function(root) {
  root.SelectedLinearAsset = function(backend, collection, typeId, singleElementEventCategory, multiElementEventCategory, isSeparableAssetType, validator) {
    this.selection = [];
    var self = this;
    this.dirty = false;
    var originalLinearAssetValue = null;
    var isSeparated = false;
    var isValid = true;
    var multipleSelected;

    this.singleElementEvent = function(eventName) {
      return singleElementEventCategory + ':' + eventName;
    };

    this.multiElementEvent = function(eventName) {
      return multiElementEventCategory + ':' + eventName;
    };

    this.splitLinearAsset = function(id, split) {
      collection.splitLinearAsset(id, split, function(splitLinearAssets) {
        self.selection = [splitLinearAssets.created, splitLinearAssets.existing];
        originalLinearAssetValue = splitLinearAssets.existing.value;
        self.dirty = true;
        collection.setSelection(self);
        eventbus.trigger(self.singleElementEvent('selected'), self);
      });
    };

    this.separate = function() {
      self.selection = collection.separateLinearAsset(_.head(self.selection));
      isSeparated = true;
      self.dirty = true;
      eventbus.trigger(self.singleElementEvent('separated'), self);
      eventbus.trigger(self.singleElementEvent('selected'), self);
    };

    this.open = function(linearAsset, singleLinkSelect) {
      multipleSelected = false;
      self.close();
      self.selection = singleLinkSelect ? [linearAsset] : collection.getGroup(linearAsset);
      originalLinearAssetValue = self.getValue();
      collection.setSelection(self);
      eventbus.trigger(self.singleElementEvent('selected'), self);
    };

    this.getLinearAsset = function(id) {
     return collection.getById(id);
    };

    this.addSelection = function(linearAssets){
      var partitioned = _.groupBy(linearAssets, isUnknown);
      var existingLinearAssets = _.uniq(partitioned[false] || [], 'id');
      var unknownLinearAssets = _.uniq(partitioned[true] || [], 'generatedId');
      self.selection = self.selection.concat(existingLinearAssets.concat(unknownLinearAssets));
    };

    this.removeSelection = function(linearAssets){
      self.selection = _.filter(self.selection, function(asset){
        if(isUnknown(asset))
          return !_.some(linearAssets, function(iasset){ return iasset.generatedId === asset.generatedId;});

        return !_.some(linearAssets, function(iasset){ return iasset.id === asset.id;});
      });
    };

    this.openMultiple = function(linearAssets) {
      multipleSelected = true;
      var partitioned = _.groupBy(linearAssets, isUnknown);
      var existingLinearAssets = _.uniq(partitioned[false] || [], 'id');
      var unknownLinearAssets = _.uniq(partitioned[true] || [], 'generatedId');
      self.selection = existingLinearAssets.concat(unknownLinearAssets);
      eventbus.trigger(self.singleElementEvent('multiSelected'));
    };

    this.close = function() {
      if (!_.isEmpty(self.selection) && !self.dirty) {
        eventbus.trigger(self.singleElementEvent('unselect'), self);
        collection.setSelection(null);
        self.selection = [];
      }
    };

    this.closeMultiple = function() {
      eventbus.trigger(self.singleElementEvent('unselect'), self);
      self.dirty = false;
      collection.setSelection(null);
      self.selection = [];
    };

    this.saveMultiple = function(value) {
      eventbus.trigger(self.singleElementEvent('saving'));
      var partition = _.groupBy(_.map(self.selection, function(item){ return _.omit(item, 'geometry'); }), isUnknown);
      var unknownLinearAssets = partition[true];
      var knownLinearAssets = partition[false];

      var payload = {
        newLimits: _.map(unknownLinearAssets, function(x) { return _.merge(x, {value: value, expired: false }); }),
        ids: _.map(knownLinearAssets, 'id'),
        value: value,
        typeId: typeId
      };
      var backendOperation = _.isUndefined(value) ? backend.deleteLinearAssets : backend.createLinearAssets;
      backendOperation(payload, function() {
        self.dirty = false;
        self.closeMultiple();
        eventbus.trigger(self.multiElementEvent('massUpdateSucceeded'), self.selection.length);
      }, function() {
        eventbus.trigger(self.multiElementEvent('massUpdateFailed'), self.selection.length);
      });
    };

    var saveSplit = function() {
      eventbus.trigger(self.singleElementEvent('saving'));
      collection.saveSplit(function() {
        self.dirty = false;
        self.close();
      });
    };

    var saveSeparation = function() {
      eventbus.trigger(self.singleElementEvent('saving'));
      collection.saveSeparation(function() {
        self.dirty = false;
        isSeparated = false;
        self.close();
      });
    };

    var saveExisting = function() {
      eventbus.trigger(self.singleElementEvent('saving'));
      var payloadContents = function() {
        if (self.isUnknown()) {
          return { newLimits: _.map(self.selection, function(item){ return _.omit(item, 'geometry'); }) };
        } else {
          return { ids: _.map(self.selection, 'id') };
        }
      };
      var payload = _.merge({value: self.getValue(), typeId: typeId}, payloadContents());
      var backendOperation = _.isUndefined(self.getValue()) ? backend.deleteLinearAssets : backend.createLinearAssets;

      backendOperation(payload, function() {
        self.dirty = false;
        self.close();
        eventbus.trigger(self.singleElementEvent('saved'));
      }, function() {
        eventbus.trigger('asset:updateFailed');
      });
    };

    var isUnknown = function(linearAsset) {
      return !_.has(linearAsset, 'id');
    };

    this.isUnknown = function() {
      return isUnknown(self.selection[0]);
    };

    this.isSplit = function() {
      return !isSeparated && !_.isEmpty(self.selection[0]) && self.selection[0].id === null;
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
      if (isSeparated) {
        var originalLinearAsset = _.cloneDeep(self.selection[0]);
        originalLinearAsset.value = originalLinearAssetValue;
        originalLinearAsset.sideCode = 1;
        collection.replaceSegments([self.selection[0]], [originalLinearAsset]);
      }
      collection.setSelection(null);
      self.selection = [];
      self.dirty = false;
      isSeparated = false;
      collection.cancelCreation();
      eventbus.trigger(self.singleElementEvent('unselect'), self);
    };

    var cancelExisting = function() {
      var newGroup = _.map(self.selection, function(s) { return _.assign({}, s, { value: originalLinearAssetValue }); });
      self.selection = collection.replaceSegments(self.selection, newGroup);
      self.dirty = false;
      eventbus.trigger(self.singleElementEvent('cancelled'), self);
    };

    this.cancel = function() {
      if (self.isSplit() || self.isSeparated()) {
        cancelCreation();
      } else {
        cancelExisting();
      }
      self.close();
    };

    this.verify = function() {
      eventbus.trigger(self.singleElementEvent('saving'));
      var knownLinearAssets = _.reject(self.selection, isUnknown);
      var payload = {ids: _.map(knownLinearAssets, 'id'), typeId: typeId};
      collection.verifyLinearAssets(payload);
      self.dirty = false;
      self.close();
    };

    this.exists = function() {
      return !_.isEmpty(self.selection);
    };

    var getProperty = function(propertyName) {
      return _.has(self.selection[0], propertyName) ? self.selection[0][propertyName] : null;
    };

    var getPropertyB = function(propertyName) {
      return _.has(self.selection[1], propertyName) ? self.selection[1][propertyName] : null;
    };

    this.getId = function() {
      return getProperty('id');
    };

    this.getValue = function() {
      var value = getProperty('value');
      return _.isNull(value) ? undefined : value;
    };

    this.getBValue = function() {
      var value = getPropertyB('value');
      return _.isNull(value) ? undefined : value;
    };

    this.getModifiedBy = function() {
      return dateutil.extractLatestModifications(self.selection, 'modifiedAt').modifiedBy;
    };

    this.getModifiedDateTime = function() {
      return dateutil.extractLatestModifications(self.selection, 'modifiedAt').modifiedAt;
    };

    this.getCreatedBy = function() {
      return self.selection.length === 1 ? getProperty('createdBy') : null;
    };

    this.getCreatedDateTime = function() {
      return self.selection.length === 1 ? getProperty('createdAt') : null;
    };

    this.getAdministrativeClass = function() {
      var value = getProperty('administrativeClass');
      return _.isNull(value) ? undefined : value;
    };

    this.getVerifiedBy = function() {
      return self.selection.length === 1 ? getProperty('verifiedBy') : null;
    };

    this.getVerifiedDateTime = function() {
      return self.selection.length === 1 ? getProperty('verifiedAt') : null;
    };

    this.get = function() {
      return self.selection;
    };

    this.count = function() {
      return self.selection.length;
    };

    this.setValue = function(value) {
      if (value != self.selection[0].value) {
        var newGroup = _.map(self.selection, function(s) { return _.assign({}, s, { value: value }); });
        self.selection = collection.replaceSegments(self.selection, newGroup);
        self.dirty = true;
        eventbus.trigger(self.singleElementEvent('valueChanged'), self, multipleSelected);
      }
    };

    this.setMultiValue = function(value) {
        var newGroup = _.map(self.selection, function(s) { return _.assign({}, s, { value: value }); });
        self.selection = collection.replaceSegments(self.selection, newGroup);
        eventbus.trigger(self.multiElementEvent('valueChanged'), self, multipleSelected);
    };

    this.setAValue = function (value) {
      if (value != self.selection[0].value) {
        var newGroup = _.assign({}, self.selection[0], { value: value });
        self.selection[0] = collection.replaceCreatedSplit(self.selection[0], newGroup);
        eventbus.trigger(self.singleElementEvent('valueChanged'), self);
      }
    };

    this.setBValue = function (value) {
      if (value != self.selection[1].value) {
        var newGroup = _.assign({}, self.selection[1], { value: value });
        self.selection[1] = collection.replaceExistingSplit(self.selection[1], newGroup);
        eventbus.trigger(self.singleElementEvent('valueChanged'), self);
      }
    };

    this.removeValue = function() {
      self.setValue(undefined);
    };

    this.removeMultiValue = function() {
      self.setMultiValue();
    };

    this.removeAValue = function() {
      self.setAValue(undefined);
    };

    this.removeBValue = function() {
      self.setBValue(undefined);
    };

    this.isDirty = function() {
      return self.dirty;
    };

    this.setDirty = function(dirtyValue) {
      self.dirty = dirtyValue;
    };

    this.isSelected = function(linearAsset) {
      return _.some(self.selection, function(selectedLinearAsset) {
        return self.isEqual(linearAsset, selectedLinearAsset);
      });
    };

    this.isSeparable = function() {
      return isSeparableAssetType &&
        getProperty('sideCode') === validitydirections.bothDirections &&
        getProperty('trafficDirection') === 'BothDirections' &&
        !self.isSplit() &&
        self.selection.length === 1;
    };

    this.isSaveable = function() {
      var valuesDiffer = function () { return (self.selection[0].value !== self.selection[1].value); };
      if (this.isDirty()) {
        if (this.isSplitOrSeparated() && valuesDiffer())
              return validator(self.selection[0].value) && validator(self.selection[1].value);

        if (!this.isSplitOrSeparated())
          return validator(self.selection[0].value);
        }
      return false;
    };

    this.validator = validator;

    this.isEqual = function(a, b) {
      return (_.has(a, 'generatedId') && _.has(b, 'generatedId') && (a.generatedId === b.generatedId)) ||
        ((!isUnknown(a) && !isUnknown(b)) && (a.id === b.id));
    };

    this.isSuggested = function() {
        var  suggestedProp = getProperty('isSuggested');
      return !_.isEmpty(suggestedProp) && !!parseInt(suggestedProp) ||
          _.some(self.selection, function(asset) {
        return asset.value.isSuggested;});
    };
  };
})(this);
