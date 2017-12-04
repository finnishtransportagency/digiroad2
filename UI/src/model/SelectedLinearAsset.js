(function(root) {
  root.SelectedLinearAsset = function(backend, collection, typeId, singleElementEventCategory, multiElementEventCategory, isSeparableAssetType, validator) {
    var selection = [];
    var self = this;
    var dirty = false;
    var originalLinearAssetValue = null;
    var isSeparated = false;

    var singleElementEvent = function(eventName) {
      return singleElementEventCategory + ':' + eventName;
    };

    var multiElementEvent = function(eventName) {
      return multiElementEventCategory + ':' + eventName;
    };

    this.splitLinearAsset = function(id, split) {
      collection.splitLinearAsset(id, split, function(splitLinearAssets) {
        selection = [splitLinearAssets.created, splitLinearAssets.existing];
        originalLinearAssetValue = splitLinearAssets.existing.value;
        dirty = true;
        collection.setSelection(self);
        eventbus.trigger(singleElementEvent('selected'), self);
      });
    };

    this.separate = function() {
      selection = collection.separateLinearAsset(_.first(selection));
      isSeparated = true;
      dirty = true;
      eventbus.trigger(multiElementEvent('fetched'), collection.getAll());
      eventbus.trigger(singleElementEvent('separated'), self);
      eventbus.trigger(singleElementEvent('selected'), self);
    };

    this.open = function(linearAsset, singleLinkSelect) {
      self.close();
      selection = singleLinkSelect ? [linearAsset] : collection.getGroup(linearAsset);
      originalLinearAssetValue = self.getValue();
      collection.setSelection(self);
      eventbus.trigger(singleElementEvent('selected'), self);
    };

    this.getLinearAsset = function(id) {
     return collection.getById(id);
    };

    this.getTypeId = function(){
      return typeId;
    };

    this.openMultiple = function(linearAssets) {
      var partitioned = _.groupBy(linearAssets, isUnknown);
      var existingLinearAssets = _.unique(partitioned[false] || [], 'id');
      var unknownLinearAssets = _.unique(partitioned[true] || [], 'generatedId');
      selection = existingLinearAssets.concat(unknownLinearAssets);
      eventbus.trigger(singleElementEvent('multiSelected'));
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
      var partition = _.groupBy(_.map(selection, function(item){ return _.omit(item, 'geometry'); }), isUnknown);
      var unknownLinearAssets = partition[true];
      var knownLinearAssets = partition[false];

      var payload = {
        newLimits: _.map(unknownLinearAssets, function(x) { return _.merge(x, {value: value, expired: false }); }),
        ids: _.pluck(knownLinearAssets, 'id'),
        value: value,
        typeId: typeId
      };
      var backendOperation = _.isUndefined(value) ? backend.deleteLinearAssets : backend.createLinearAssets;
      backendOperation(payload, function() {
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
          return { newLimits: _.map(selection, function(item){ return _.omit(item, 'geometry'); }) };
        } else {
          return { ids: _.pluck(selection, 'id') };
        }
      };
      var payload = _.merge({value: self.getValue(), typeId: typeId}, payloadContents());
      var backendOperation = _.isUndefined(self.getValue()) ? backend.deleteLinearAssets : backend.createLinearAssets;

      backendOperation(payload, function() {
        dirty = false;
        self.close();
        eventbus.trigger(singleElementEvent('saved'));
      }, function() {
        eventbus.trigger('asset:updateFailed');
      });
    };

    var isUnknown = function(linearAsset) {
      return !_.has(linearAsset, 'id');
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
        var originalLinearAsset = _.cloneDeep(selection[0]);
        originalLinearAsset.value = originalLinearAssetValue;
        originalLinearAsset.sideCode = 1;
        collection.replaceSegments([selection[0]], [originalLinearAsset]);
      }
      collection.setSelection(null);
      selection = [];
      dirty = false;
      isSeparated = false;
      collection.cancelCreation();
    };

    var cancelExisting = function() {
      var newGroup = _.map(selection, function(s) { return _.assign({}, s, { value: originalLinearAssetValue }); });
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

    this.verify = function() {
      var knownLinearAssets = _.reject(selection, isUnknown);
      var payload = {ids: _.pluck(knownLinearAssets, 'id'), typeId: typeId};
      backend.verifyLinearAssets(payload, function() {
        dirty = false;
        self.close();
        eventbus.trigger(singleElementEvent('saved'));
      }, function() {
        eventbus.trigger('asset:verificationFailed');
      });
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

    this.getAdministrativeClass = function() {
      var value = getProperty('administrativeClass');
      return _.isNull(value) ? undefined : value;
    };

    this.getVerifiedBy = function() {
      return selection.length === 1 ? getProperty('verifiedBy') : null;
    };

    this.getVerifiedDateTime = function() {
      return selection.length === 1 ? getProperty('verifiedAt') : null;
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

    function isValueDifferent(selection){
      if(selection.length == 1) return true;

      var nonEmptyValues = _.map(selection, function (select) {
        return  _.filter(select.value, function(val){ return !_.isEmpty(val.value); });
      });
      var zipped = _.zip(nonEmptyValues[0], nonEmptyValues[1]);
      var mapped = _.map(zipped, function (zipper) {
          if(!zipper[1] || !zipper[0])
            return true;
          else
            return zipper[0].value !== zipper[1].value;
      });
      return _.contains(mapped, true);
    }

    function getRequiredFields(properties){
      return _.filter(properties, function (property) {
        return (property.publicId === "huoltotie_kayttooikeus") || (property.publicId === "huoltotie_huoltovastuu");
      });
    }

    function checkFormMandatoryFields(formSelection) {
        if (_.isUndefined(formSelection.value)) return true;
        var requiredFields = getRequiredFields(formSelection.value);
        return !_.some(requiredFields, function(fields){ return fields.value === ''; });
    }

    function checkFormsMandatoryFields(formSelections) {
      var mandatorySelected = !_.some(formSelections, function(formSelection){ return !checkFormMandatoryFields(formSelection); });
      return mandatorySelected;
    }

    this.setAValue = function (value) {
      if (value != selection[0].value) {
        selection[0].value = value;
        eventbus.trigger(singleElementEvent('valueChanged'), self);
      }
    };

    this.setBValue = function (value) {
      if (value != selection[1].value) {
        selection[1].value = value;
        eventbus.trigger(singleElementEvent('valueChanged'), self);
      }
    };

    this.removeValue = function() {
      self.setValue(undefined);
    };

    this.removeAValue = function() {
      self.setAValue(undefined);
    };

    this.removeBValue = function() {
      self.setBValue(undefined);
    };

    this.isDirty = function() {
      return dirty;
    };

    this.isSelected = function(linearAsset) {
      return _.some(selection, function(selectedLinearAsset) {
        return isEqual(linearAsset, selectedLinearAsset);
      });
    };

    this.isSeparable = function() {
      return isSeparableAssetType &&
        getProperty('sideCode') === validitydirections.bothDirections &&
        getProperty('trafficDirection') === 'BothDirections' &&
        !self.isSplit() &&
        selection.length === 1;
    };

    this.isSaveable = function() {
      var valuesDiffer = function () { return (selection[0].value !== selection[1].value); };
      if (this.isDirty()) {
        switch (validator.name) {
          case 'maintenanceRoad':
            if(this.isSplitOrSeparated())
              return checkFormsMandatoryFields(selection) && isValueDifferent(selection);
            return checkFormsMandatoryFields(selection);
          default:
            if (this.isSplitOrSeparated() && valuesDiffer())
              return validator(selection[0].value) && validator(selection[1].value);

            if (!this.isSplitOrSeparated())
              return validator(selection[0].value);
        }
      }
      return false;
    };

    this.validator = validator;

    var isEqual = function(a, b) {
      return (_.has(a, 'generatedId') && _.has(b, 'generatedId') && (a.generatedId === b.generatedId)) ||
        ((!isUnknown(a) && !isUnknown(b)) && (a.id === b.id));
    };
  };
})(this);
