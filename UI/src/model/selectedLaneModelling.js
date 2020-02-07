(function(root) {
  root.SelectedLaneModelling = function(backend, collection, typeId, singleElementEventCategory, multiElementEventCategory, isSeparableAssetType) {
    var lanesFetched = [];
    var selection = [];
    var selectedRoadlink = null;
    var assetsToBeExpired = [];
    var assetsToBeRemoved = [];
    var self = this;
    var dirty = false;
    var originalLinearAssetValue = null;
    var multipleSelected;

    var initial_road_number;
    var initial_road_part_number;
    var initial_distance;
    var end_road_part_number;
    var end_distance;

    var getLane = function (laneNumber) {
        return _.find(selection, function (lane){
          return _.find(lane.properties, function (property) {
            return property.publicId == "lane_code" && _.head(property.values).value == laneNumber;
          });
        });
    };

    var reorganizeLanes = function (laneNumber) {
      var lanesToUpdate = _.map(selection, function (lane){
        var foundValidProperty =  _.find(lane.properties, function (property) {
          var value = _.head(property.values).value;
          return property.publicId == "lane_code" && value > laneNumber && ((value % 2 !== 0 && laneNumber % 2 !== 0) || (value % 2 === 0 && laneNumber % 2 === 0));
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
        });
    };

    var singleElementEvent = function(eventName) {
      return singleElementEventCategory + ':' + eventName;
    };

    var multiElementEvent = function(eventName) {
      return multiElementEventCategory + ':' + eventName;
    };

    this.splitLinearAsset = function(laneNumber, split) {
      collection.splitLinearAsset(getLane(laneNumber), split, function(splitLinearAssets) {
        self.removeLane(laneNumber, undefined, true);
        selection.push(splitLinearAssets.created, splitLinearAssets.existing);
        dirty = true;
        eventbus.trigger('laneModellingForm: reload');
      });
    };

    this.open = function(linearAsset, singleLinkSelect) {
      multipleSelected = false;
      self.close();
      var linearAssets = singleLinkSelect ? [linearAsset] : collection.getGroup(linearAsset);
      selectedRoadlink = linearAsset;
      backend.getLanesByLinkIdAndSidecode(linearAsset.linkId, linearAsset.sideCode, function(asset) {
        _.forEach(asset, function (lane) {
          lane.linkId = _.map(linearAssets, function (linearAsset) {
            return linearAsset.linkId;
          });
          lane.selectedLinks = linearAssets;
        });
        originalLinearAssetValue = _.cloneDeep(asset);
        selection = _.cloneDeep(asset);
        lanesFetched = _.cloneDeep(asset);
        collection.setSelection(self);
        assetsToBeExpired=[];
        assetsToBeRemoved=[];
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

      initial_road_number = selectedRoadlink.roadNumber;
      initial_road_part_number = selectedRoadlink.roadPartNumber;
      initial_distance = selectedRoadlink.startAddrMValue;

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
        newLimits: _.map(unknownLinearAssets, function(x) { return _.merge(x, {value: value, isExpired: false }); }),
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

    function omitUnrelevantProperties(lanes){
      return _.map(lanes, function (lane) {
        return _.omit(lane, ['linkId', 'sideCode', 'selectedLinks', 'points', 'marker', 'initial_road_number',
          'initial_road_part_number', 'initial_distance', 'end_road_part_number', 'end_distance']);
      });
    }

    var saveExisting = function(isAddByRoadAddressActive) {
      eventbus.trigger(singleElementEvent('saving'));

      var linkIds = selection[0].linkId;
      var sideCode = selection[0].sideCode;

      var lanes = omitUnrelevantProperties(selection);

      var payload;
      if(isAddByRoadAddressActive) {

        payload = {
          isAddByRoadAddress: true,
          sideCode: sideCode,
          initial_road_number: initial_road_number,
          initial_road_part_number: initial_road_part_number,
          initial_distance: initial_distance,
          end_road_part_number: end_road_part_number,
          end_distance: end_distance,
          lanes: lanes
        };
      }else{

        payload = {
          linkIds: linkIds,
          sideCode: sideCode,
          lanes: lanes.concat(omitUnrelevantProperties(assetsToBeExpired)).concat(omitUnrelevantProperties(assetsToBeRemoved))
        };
      }

      var backendOperation = isAddByRoadAddressActive ? backend.updateLaneAssetsByRoadAddress : backend.updateLaneAssets;

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
        return _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && _.head(property.values).value == laneNumber;
        });
      }));
    };

    this.isSplit = function(laneNumber) {
      if(_.isUndefined(laneNumber))
        return false;

      var lane = _.filter(selection, function (lane){
        return _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && _.head(property.values).value == laneNumber;
        });
      });

      return lane.length > 1;
    };

    this.configurationIsCut = function() {
      var lane = _.find(selection, function (lane){
        return !_.isUndefined(lane.marker);
      });

      return !_.isUndefined(lane);
    };

    this.isAddByRoadAddress = function() {
      var lane = _.find(selection, function (lane){
        return _.find(lane.properties, function (property) {
          return property.publicId == "initial_road_number";
        });
      });

      return !_.isUndefined(lane);
    };

    this.lanesCutAreEqual = function() {
      var laneNumbers = _.map(selection, function (lane){
        return _.head(_.find(lane.properties, function (property) {
          return property.publicId == "lane_code";
        }).values).value;
      });

      var cuttedLaneNumbers = _.transform(_.countBy(laneNumbers), function(result, count, value) {
        if (count > 1) result.push(value);
      }, []);

      return _.some(cuttedLaneNumbers, function (laneNumber){
        var lanes = _.filter(selection, function (lane){
          return _.find(lane.properties, function (property) {
            return property.publicId == "lane_code" && _.head(property.values).value == laneNumber;
          });
        });

        return _.isEqual(lanes[0].properties, lanes[1].properties);
      });
    };

    this.isOuterLane= function(laneNumber) {
      var lane = _.find(selection, function (lane){
        return _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && _.head(property.values).value == parseInt(laneNumber) + 2;
        });
      });

      return _.isUndefined(lane);
    };

    this.isSplitOrSeparated = function() {
      return this.isSplit();
    };

    this.save = function(isAddByRoadAddressActive) {
        saveExisting(isAddByRoadAddressActive);
    };

    var cancelExisting = function() {
      selection = lanesFetched;
      dirty = false;
      eventbus.trigger(singleElementEvent('valueChanged'), self);
    };

    this.cancel = function() {
      cancelExisting();
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
      var endValue = _.head(currentPropertyValue.values).value;
      switch(currentPropertyValue.publicId) {
        case "end_road_part_number":
          end_road_part_number = endValue;
          break;
        case "end_distance":
          end_distance = endValue;
          break;
      }

      _.forEach(selection, function (lane) {
        var currentLaneNumber = _.head(_.find(lane.properties,function (prop) {
          return prop.publicId == "lane_code";
        }).values).value;

        var properties = _.filter(self.getValue(currentLaneNumber), function(property){ return property.publicId !== currentPropertyValue.publicId; });
        properties.push(currentPropertyValue);
        self.setValue(currentLaneNumber, {properties: properties});
      });
    };

    this.getValue = function(laneNumber) {
      var value = getProperty(getLane(laneNumber), 'properties');
      return value;
    };

    this.getAValue = function(laneNumber) {
      var lane = _.find(selection, function (lane){
        return lane.marker == 'A' && _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && _.head(property.values).value == laneNumber;
        });
      });

      var value = getProperty(lane, 'properties');
      return value;
    };

    this.getBValue = function(laneNumber) {
      var lane = _.find(selection, function (lane){
        return lane.marker == 'B' && _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && _.head(property.values).value == laneNumber;
        });
      });

      var value = getProperty(lane, 'properties');
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

    this.removeLane = function(laneNumber, sidecode, splited) {
        var laneIndex = _.findIndex(selection, function (lane) {
          return (_.isUndefined(sidecode) || lane.marker == sidecode) && _.find(lane.properties, function (property) {
            return property.publicId == "lane_code" && _.head(property.values).value == laneNumber;
          });
        });

      var removeLane = selection.splice(laneIndex,1)[0];
      removeLane.isDeleted = true;

      if((removeLane.id !== 0 || !splited) && _.isUndefined(removeLane.marker))
        assetsToBeRemoved.push(removeLane);

      reorganizeLanes(laneNumber);
      dirty = true;
    };

    this.expireLane = function(laneNumber, sidecode) {
      var laneIndex = _.findIndex(selection, function (lane) {
        return (_.isUndefined(sidecode) || lane.marker == sidecode) && _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && _.head(property.values).value == laneNumber;
        });
      });

      var expireLane = selection.splice(laneIndex,1)[0];
      expireLane.isExpired = true;
      assetsToBeExpired.push(expireLane);

      reorganizeLanes(laneNumber);
      dirty = true;
    };

    this.setValue = function(laneNumber, value) {
      var laneIndex = _.findIndex(selection, function (lane) {
        return _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && _.head(property.values).value == laneNumber;
        });
      });
      var newGroup = _.assign([], selection[laneIndex].properties, value);
      if(!dirty && _.isEqual(selection[laneIndex].properties, newGroup.properties)){
        dirty = false;
      }else{
        selection[laneIndex].properties = newGroup.properties;
        dirty = true;
      }
      eventbus.trigger(singleElementEvent('valueChanged'), self, laneNumber);
    };

    this.setMultiValue = function(value) {
      var newGroup = _.map(selection, function(s) { return _.assign({}, s, { value: value }); });
      selection = collection.replaceSegments(selection, newGroup);
      eventbus.trigger(multiElementEvent('valueChanged'), self);
    };

    this.setAValue = function (laneNumber, value) {
      var laneIndex = _.findIndex(selection, function (lane) {
        return lane.marker == 'A'  && _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && _.head(property.values).value == laneNumber;
        });
      });
      var newGroup = _.assign([], selection[laneIndex].properties, value);
        selection[laneIndex].properties = newGroup.properties;
        dirty = true;

      eventbus.trigger(singleElementEvent('valueChanged'), self, laneNumber);
    };

    this.setBValue = function (laneNumber, value) {
      var laneIndex = _.findIndex(selection, function (lane) {
        return lane.marker == 'B'  && _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && _.head(property.values).value == laneNumber;
        });
      });
      var newGroup = _.assign([], selection[laneIndex].properties, value);
      selection[laneIndex].properties = newGroup.properties;
      dirty = true;

      eventbus.trigger(singleElementEvent('valueChanged'), self, laneNumber);
    };

    this.removeValue = function(laneNumber) {
      self.setValue(laneNumber, undefined);
    };

    this.removeMultiValue = function() {
      self.setMultiValue();
    };

    this.removeAValue = function(laneNumber) {
      self.setAValue(laneNumber, undefined);
    };

    this.removeBValue = function(laneNumber) {
      self.setBValue(laneNumber, undefined);
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
  };
})(this);
