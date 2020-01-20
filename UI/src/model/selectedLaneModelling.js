(function(root) {
  root.SelectedLaneModelling = function(backend, collection, typeId, singleElementEventCategory, multiElementEventCategory, isSeparableAssetType) {
    var lanesFetched = [];
    var selection = [];
    var selectedRoadlink = null;
    var assetsToBeExpired = [];
    var self = this;
    var dirty = false;
    var originalLinearAssetValue = null;
    var isSeparated = false;
    var isValid = true;
    var multipleSelected;

    var getLane = function (laneNumber) {
        return _.find(selection, function (lane){
          return _.find(lane.properties, function (property) {
            return property.publicId == "lane_code" && property.values[0].value == laneNumber;
          });
        });
    };

    var reorganizeLanes = function (laneNumber) {
      var lanesToUpdate = _.map(selection, function (lane){
        var foundValidProperty =  _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && property.values[0].value > laneNumber && ((property.values[0].value % 2 !== 0 && laneNumber % 2 !== 0) || (property.values[0].value % 2 === 0 && laneNumber % 2 === 0));
        });

        if(_.isUndefined(foundValidProperty)){
          return undefined;
        }else{
          return lane;
        }
      });

      var listLanesIndexes = _.filter(_.map(lanesToUpdate,function (laneToUpdate) {
        return _.findIndex(selection, function (lane) {
          return lane == laneToUpdate;
        });
      }),function (index) {
        return index != "-1";
      });

      if (!_.isEmpty(listLanesIndexes))
        _.forEach(listLanesIndexes, function (number) {
          var propertyIndex =  _.findIndex(selection[number].properties, function (property) {
            return property.publicId == "lane_code";
          });
          selection[number].properties[propertyIndex].values[0].value = parseInt(selection[number].properties[propertyIndex].values[0].value) - 2;
          // selection[number].values[0].value = parseInt(selection[number].values[0].value) - 2;
        });
    };

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
      selection = collection.separateLinearAsset(_.head(selection));
      isSeparated = true;
      dirty = true;
      eventbus.trigger(singleElementEvent('separated'), self);
      eventbus.trigger(singleElementEvent('selected'), self);
    };

    this.open = function(linearAsset, singleLinkSelect) {
      multipleSelected = false;
      self.close();
      var linearAssets = singleLinkSelect ? [linearAsset] : collection.getGroup(linearAsset);
      selectedRoadlink = linearAsset;
      backend.getLanesByLinkId(linearAsset.linkId, function(asset) {
        _.forEach(asset, function (lane) {
          lane.linkId = _.map(linearAssets, function (linearAsset) {
            return linearAsset.linkId;
          });
        });
        originalLinearAssetValue = _.cloneDeep(asset);  //same as lanes fetched?
        selection = _.cloneDeep(asset);
        lanesFetched = _.cloneDeep(asset);
        collection.setSelection(self);  //is this needed?
        assetsToBeExpired=[];
        eventbus.trigger(singleElementEvent('selected'), self);
      });
    };

    this.getLinearAsset = function(id) {
      return collection.getById(id);
    };

    this.getSelectedRoadlink = function() {
      return selectedRoadlink;
    };

    this.setInitialRoadFields = function(){
      var roadNumberElement = {publicId: "initial_road_number", propertyType: "read_only_number", required: 'required', values: [{value: selectedRoadlink.roadNumber}]};
      var roadPartNumberElement = {publicId: "initial_road_part_number", propertyType: "read_only_number", required: 'required', values: [{value: selectedRoadlink.roadPartNumber}]};
      var startAddrMValueElement = {publicId: "initial_distance", propertyType: "read_only_number", required: 'required', values: [{value: selectedRoadlink.startAddrMValue}]};

      _.forEach(selection, function (lane) {
        lane.properties.push(roadNumberElement, roadPartNumberElement, startAddrMValueElement);
      });
    };

    this.addSelection = function(linearAssets){
      var partitioned = _.groupBy(linearAssets, isUnknown);
      var existingLinearAssets = _.uniq(partitioned[false] || [], 'id');
      var unknownLinearAssets = _.uniq(partitioned[true] || [], 'generatedId');
      selection = selection.concat(existingLinearAssets.concat(unknownLinearAssets));
    };

    this.removeSelection = function(linearAssets){
      selection = _.filter(selection, function(asset){
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
      eventbus.trigger(singleElementEvent('unselect'), self);
      dirty = false;
      collection.setSelection(null);
      selection = [];
    };

    this.saveMultiple = function(value) {
      eventbus.trigger(singleElementEvent('saving'));
      var partition = _.groupBy(_.map(selection, function(item){ return _.omit(item, 'geometry'); }), isUnknown);
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
        dirty = false;
        self.closeMultiple();
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
      // var payloadContents = function() {
      //   if (self.isUnknown()) {
      //     return { newLimits: _.map(selection, function(item){ return _.omit(item, 'geometry'); }) };
      //   } else {
      //     return { ids: _.map(selection, 'id') };
      //   }
      // };
      var payload = {assets: selection.concat(assetsToBeExpired), typeId: typeId};
      var backendOperation = backend.updateLaneAssets;

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

    this.isUnknown = function(laneNumber) {
      return isUnknown(_.find(selection, function (lane){
        // return lane.properties.lane_code == laneNumber;
        return _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && property.values[0].value == laneNumber;
        });
      }));
    };

    this.isSplit = function(laneNumber) {
      var lane = _.find(selection, function (lane){
        // return lane.properties.lane_code == laneNumber;
        return _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && property.values[0].value == laneNumber;
        });
      });

      return !isSeparated && !_.isEmpty(lane) && lane.id === null;
    };

    this.isSeparated = function() {
      return isSeparated;
    };

    this.isSplitOrSeparated = function() {
      return this.isSplit() || this.isSeparated();
    };

    this.save = function() {
        saveExisting();
    };

    var cancelCreation = function() {
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
      eventbus.trigger(singleElementEvent('unselect'), self);
    };

    var cancelExisting = function() {
      // var newGroup = _.map(selection, function(s) { return _.assign({}, s, { value: originalLinearAssetValue }); });
      // selection = collection.replaceSegments(selection, newGroup);
      selection = lanesFetched;
      dirty = false;
      // eventbus.trigger(singleElementEvent('cancelled'), self);
      eventbus.trigger(singleElementEvent('valueChanged'), self, multipleSelected);
    };

    this.cancel = function() {
      // if (self.isSplit() || self.isSeparated()) {
      //   cancelCreation();
      // } else {
      cancelExisting();
      // }
      self.close();
    };

    this.verify = function() {
      eventbus.trigger(singleElementEvent('saving'));
      var knownLinearAssets = _.reject(selection, isUnknown);
      var payload = {ids: _.map(knownLinearAssets, 'id'), typeId: typeId};
      collection.verifyLinearAssets(payload);
      dirty = false;
      self.close();
    };

    this.exists = function() {
      return !_.isEmpty(selection);
    };

    var getProperty = function(lane, propertyName) {
      return _.has(lane, propertyName) ? lane[propertyName] : null;
    };

    this.getId = function() {
      return getProperty('id');
    };

    this.setEndAddressesValues = function(currentPropertyValue) {
      _.forEach(selection, function (lane) {
        var currentLaneNumber = _.find(lane.properties,function (prop) {
          return prop.publicId == "lane_code";
        }).values[0].value;

        var properties = _.filter(this.getValue(currentLaneNumber), function(property){ return property.publicId !== currentPropertyValue.publicId; });
        properties.push(currentPropertyValue);
        this.setValue(currentLaneNumber, {properties: properties});
      });
    };

    this.getValue = function(laneNumber) {
      var value = getProperty(getLane(laneNumber), 'properties');
      return value;
    };

    this.getModifiedBy = function() {
      return dateutil.extractLatestModifications(selection, 'modifiedAt').modifiedBy;
    };

    this.getModifiedDateTime = function() {
      return dateutil.extractLatestModifications(selection, 'modifiedAt').modifiedAt;
    };

    this.getCreatedBy = function(laneNumber) {
      return getProperty(getLane(laneNumber), 'createdBy');
    };

    this.getCreatedDateTime = function(laneNumber) {
      return getProperty(getLane(laneNumber), 'createdAt');
    };

    this.getAdministrativeClass = function(laneNumber) {
      var value = getProperty(getLane(laneNumber), 'administrativeClass');
      return _.isNull(value) ? undefined : value;
    };

    this.getVerifiedBy = function(laneNumber) {
      return getProperty(getLane(laneNumber), 'verifiedBy');
    };

    this.getVerifiedDateTime = function(laneNumber) {
      return getProperty(getLane(laneNumber), 'verifiedAt');
    };

    this.get = function() {
      return selection;
    };

    this.count = function() {
      return selection.length;
    };

    this.setNewLane = function(laneNumber) {
      var newLane;
      if(laneNumber.toString()[1] == 2){
        newLane = _.cloneDeep(getLane(laneNumber-1));
      }else{
        newLane = _.cloneDeep(getLane(laneNumber-2));
      }

      var outerLaneIsMainLane = laneNumber.toString()[1] == 2 || laneNumber.toString()[1] == 3;

      var properties = _.filter(newLane.properties, function (property) {
        if(outerLaneIsMainLane)
          return property.publicId != "lane_code" && property.publicId != "lane_type";

        return property.publicId != "lane_code";
      });

      var laneCodeProperty = {publicId: "lane_code", propertyType: "read_only_number", required: "required", values: [{value: laneNumber}]};
      properties.push(laneCodeProperty);
      newLane.properties = properties;

      newLane.id = 0;
      selection.push(newLane);
    };

    this.removeLane = function(laneNumber) {
      var laneIndex = _.findIndex(selection, function (lane) {
        return _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && property.values[0].value == laneNumber;
        });
      });

      selection.splice(laneIndex,1);
      reorganizeLanes(laneNumber);
    };

    this.expireLane = function(laneNumber) {
      var laneIndex = _.findIndex(selection, function (lane) {
        return _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && property.values[0].value == laneNumber;
        });
      });

      var expireLane = selection.splice(laneIndex,1)[0];
      expireLane.expire = true;
      assetsToBeExpired.push(expireLane);

      reorganizeLanes(laneNumber);
    };

    this.setValue = function(laneNumber, value) {
      var laneIndex = _.findIndex(selection, function (lane) {
        return _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && property.values[0].value == laneNumber;
        });
      });
        // var newGroup = _.map(selection[laneIndex], function(s) { return _.assign({}, s, value); });
        var newGroup = _.assign([], selection[laneIndex].properties, value);
        // selection[laneIndex] = collection.replaceSegments(selection, selection[laneIndex], newGroup);
        selection[laneIndex].properties = newGroup.properties;
        if(selection == lanesFetched){
          dirty = false;
        }else{
          dirty = true;
        }
        eventbus.trigger(singleElementEvent('valueChanged'), self, multipleSelected);
    };

    this.setMultiValue = function(value) {
      var newGroup = _.map(selection, function(s) { return _.assign({}, s, { value: value }); });
      selection = collection.replaceSegments(selection, newGroup);
      eventbus.trigger(multiElementEvent('valueChanged'), self, multipleSelected);
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
      return _.includes(mapped, true);
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
        var newGroup = _.assign({}, selection[0], { value: value });
        selection[0] = collection.replaceCreatedSplit(selection[0], newGroup);
        eventbus.trigger(singleElementEvent('valueChanged'), self);
      }
    };

    this.setBValue = function (value) {
      if (value != selection[1].value) {
        var newGroup = _.assign({}, selection[1], { value: value });
        selection[1] = collection.replaceExistingSplit(selection[1], newGroup);
        eventbus.trigger(singleElementEvent('valueChanged'), self);
      }
    };

    this.removeValue = function(laneNumber) {
      self.setValue(laneNumber, undefined);
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
      return dirty;
    };

    this.setDirty = function(dirtyValue) {
      dirty = dirtyValue;
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
            if (this.isSplitOrSeparated() && valuesDiffer())
              return true;

            if (!this.isSplitOrSeparated())
              return true;
      }
      return false;
    };

    var isEqual = function(a, b) {
      return (_.has(a, 'generatedId') && _.has(b, 'generatedId') && (a.generatedId === b.generatedId)) ||
        ((!isUnknown(a) && !isUnknown(b)) && (a.id === b.id));
    };

    this.requiredPropertiesMissing = function (formStructure) {

      var requiredFields = _.filter(formStructure.fields, function(form) { return form.required; });

      var assets = this.isSplitOrSeparated() ? _.filter(selection, function(asset){ return asset.value; }) : selection;

      return !_.every(assets, function(asset){

        return _.every(requiredFields, function(field){
          if(!asset.value || _.isEmpty(asset.value))
            return false;

          var property  = _.find(asset.value.properties, function(p){ return p.publicId === field.publicId;});

          if(!property)
            return false;

          if(_.isEmpty(property.values))
            return false;

          return _.some(property.values, function(value){ return value && !_.isEmpty(value.value); });
        });
      });
    };

    this.isSplitOrSeparatedEqual = function(){
      if (_.filter(selection, function(p){return p.value;}).length <= 1)
        return false;

      return _.every(selection[0].value.properties, function(property){
        var iProperty =  _.find(selection[1].value.properties, function(p){ return p.publicId === property.publicId; });
        if(!iProperty)
          return false;

        return _.isEqual(property.values, iProperty.values);
      });
    };

    this.hasValidValues = function () {
      return isValid;
    };

    this.setValidValues = function (valid) {
      isValid = valid;
    };
  };
})(this);
