(function(root) {
  root.SelectedLaneModelling = function(backend, collection, typeId, singleElementEventCategory, multiElementEventCategory, isSeparableAssetType) {
    SelectedLinearAsset.call(this, backend, collection, typeId, singleElementEventCategory, multiElementEventCategory, isSeparableAssetType);
    var lanesFetched = [];
    var selectedRoadlink = null;
    var assetsToBeExpired = [];
    var assetsToBeRemoved = [];
    var self = this;
    var linksSelected = null;
    var currentLane;

    var initial_road_number;
    var initial_road_part_number;
    var initial_distance;
    var end_road_part_number;
    var end_distance;
    var track;

    function getLaneCodeValue(lane) {
      return _.head(_.find(lane.properties, {'publicId': 'lane_code'}).values).value;
    }

    this.getLane = function (laneNumber, marker) {
        return _.find(self.selection, function (lane){
          return (_.isEmpty(marker) || lane.marker == marker) && _.find(lane.properties, function (property) {
            return property.publicId === "lane_code" && _.head(property.values).value == laneNumber;
          });
        });
    };

    this.getCurrentLaneNumber = function() {
      if(!_.isUndefined(currentLane)) {
        return getLaneCodeValue(currentLane);
      }
    };

    this.getCurrentLane = function () { return currentLane; };

    this.setCurrentLane = function (lane) { currentLane = self.getLane(lane); };

    var reorganizeLanes = function (laneNumber) {
      var lanesToUpdate = _.map(self.selection, function (lane){
        var foundValidProperty =  _.find(lane.properties, function (property) {
          if(_.isEmpty(property.values))
            return false;

          var value = _.head(property.values).value;
          return property.publicId === "lane_code" && value > laneNumber && ((value % 2 !== 0 && laneNumber % 2 !== 0) || (value % 2 === 0 && laneNumber % 2 === 0));
        });

        return _.isUndefined(foundValidProperty) ? foundValidProperty : lane;
      });

      var listLanesIndexes = _.filter(_.map(lanesToUpdate,function (laneToUpdate) {
        return _.findIndex(self.selection, function (lane) {
          return lane == laneToUpdate;
        });
      }),function (index) {
        return index != "-1";
      });

      if (!_.isEmpty(listLanesIndexes))
        _.forEach(listLanesIndexes, function (number) {
          var propertyIndex =  _.findIndex(self.selection[number].properties, function (property) {
            return property.publicId === "lane_code";
          });
          self.selection[number].properties[propertyIndex].values[0].value = parseInt(self.selection[number].properties[propertyIndex].values[0].value) - 2;
        });
    };

    var giveSplitMarkers = function(lanes){
      var numberOfLanesByLaneCode = _.countBy(lanes, getLaneCodeValue);

      var laneCodesToPutMarkers = _.filter(_.keys(numberOfLanesByLaneCode), function(key){
        return numberOfLanesByLaneCode[key] > 1;
      });

      var lanesSortedByLaneCode = _.sortBy(lanes, getLaneCodeValue);

      var duplicateLaneCounter = 0;
      return _.map(lanesSortedByLaneCode, function (lane) {
        if(_.includes(laneCodesToPutMarkers, getLaneCodeValue(lane).toString())) {
          if (duplicateLaneCounter === 0){
            lane.marker = 'A';
            duplicateLaneCounter++;
          }else{
            lane.marker = 'B';
            duplicateLaneCounter--;
          }
        }
        return lane;
      });
    };

    self.splitLinearAsset = function(laneNumber, split) {
      collection.splitLinearAsset(self.getLane(laneNumber), split, function(splitLinearAssets) {
        if (self.getLane(laneNumber).id === 0) {
          self.removeLane(laneNumber);
        } else {
          self.expireLane(laneNumber);
        }

        self.selection.push(splitLinearAssets.created, splitLinearAssets.existing);
        self.dirty = true;
        eventbus.trigger('laneModellingForm: reload');
      });
    };

    self.open = function(linearAsset, singleLinkSelect) {
      self.close();
      var linearAssets = singleLinkSelect ? [linearAsset] : collection.getGroup(linearAsset);
      selectedRoadlink = linearAsset;
      backend.getLanesByLinkIdAndSidecode(linearAsset.linkId, linearAsset.sideCode, function(asset) {
        _.forEach(asset, function (lane) {
          lane.linkIds = _.map(linearAssets, function (linearAsset) {
            return linearAsset.linkId;
          });
          lane.selectedLinks = linearAssets;
        });
        var lanesWithSplitMarkers = giveSplitMarkers(asset);
        self.selection = lanesWithSplitMarkers;
        lanesFetched = lanesWithSplitMarkers;
        linksSelected = linearAssets;
        collection.setSelection(self);
        assetsToBeExpired=[];
        assetsToBeRemoved=[];
        eventbus.trigger(self.singleElementEvent('selected'), self);
      });
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
      track = selectedRoadlink.track;

      _.forEach(self.selection, function (lane) {
        lane.properties.push(roadNumberElement, roadPartNumberElement, startAddrMValueElement);
      });
    };

    self.isSplit = function() {
      var laneNumber = self.getCurrentLaneNumber();
      if(_.isUndefined(laneNumber))
        return false;

      var lane = _.filter(self.selection, function (lane){
        return _.find(lane.properties, function (property) {
          return property.publicId === "lane_code" && _.head(property.values).value == laneNumber;
        });
      });

      return lane.length > 1;
    };

    this.configurationIsCut = function() {
      var lane = _.find(self.selection, function (lane){
        return !_.isUndefined(lane.marker);
      });

      return !_.isUndefined(lane);
    };

    this.haveNewLane = function () {
      return _.some(self.selection, function(lane){
        return lane.id === 0;
      });
    };

    this.isAddByRoadAddress = function() {
      var lane = _.find(self.selection, function (lane){
        return _.find(lane.properties, function (property) {
          return property.publicId == "initial_road_number";
        });
      });

      return !_.isUndefined(lane);
    };

    self.lanesCutAreEqual = function() {
      var laneNumbers = _.map(self.selection, getLaneCodeValue);
      var cuttedLaneNumbers = _.transform(_.countBy(laneNumbers), function(result, count, value) {
        if (count > 1) result.push(value);
      }, []);

      return _.some(cuttedLaneNumbers, function (laneNumber){
        var lanes = _.filter(self.selection, function (lane){
          return _.find(lane.properties, function (property) {
            return property.publicId == "lane_code" && _.head(property.values).value == laneNumber;
          });
        });

        return _.isEqual(lanes[0].properties, lanes[1].properties);
      });
    };

    this.isOuterLane= function(laneNumber) {
      return _.isUndefined(self.getLane(parseInt(laneNumber) + 2));
    };

    function omitUnrelevantProperties(lanes){
      return _.map(lanes, function (lane) {
        var laneWithoutUnrelevantInfo = _.omit(lane, ['linkId', 'linkIds', 'sideCode', 'selectedLinks', 'points', 'marker']);
        laneWithoutUnrelevantInfo.properties = _.filter(laneWithoutUnrelevantInfo.properties, function (prop) {
          return !_.includes(['initial_road_number', 'initial_road_part_number', 'initial_distance', 'end_road_part_number', 'end_distance'], prop.publicId);
        });
        return laneWithoutUnrelevantInfo;
      });
    }

    self.save = function(isAddByRoadAddressActive) {
      eventbus.trigger(self.singleElementEvent('saving'));

      var linkIds = _.head(self.selection).linkIds;
      var sideCode = _.head(self.selection).sideCode;

      var lanes = omitUnrelevantProperties(self.selection);

      var payload;
      if(isAddByRoadAddressActive) {
        payload = {
          sideCode: sideCode,
          laneRoadAddressInfo:{
            roadNumber: initial_road_number,
            initialRoadPartNumber: initial_road_part_number,
            initialDistance: initial_distance,
            endRoadPartNumber: parseInt(end_road_part_number),
            endDistance: parseInt(end_distance),
            track: track
          },
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
        self.dirty = false;
        self.close();
        eventbus.trigger(self.singleElementEvent('saved'));
      }, function(error) {
        jQuery('.spinner-overlay').remove();
        alert(error.responseText);
      });
    };

    var cancelExisting = function() {
      self.selection = lanesFetched;
      self.dirty = false;
      eventbus.trigger(self.singleElementEvent('valueChanged'), self);
    };

    self.cancel = function() {
      cancelExisting();
      self.close();
      eventbus.trigger(self.singleElementEvent('cancelled'), self);
    };

    var getProperty = function(lane, propertyName) {
      return _.has(lane, propertyName) ? lane[propertyName] : null;
    };

    this.setEndAddressesValues = function(currentPropertyValue) {
      var endValue = _.head(currentPropertyValue.values);
      switch(currentPropertyValue.publicId) {
        case "end_road_part_number":
          end_road_part_number = _.isEmpty(endValue) ? endValue : endValue.value;
          break;
        case "end_distance":
          end_distance = _.isEmpty(endValue) ? endValue : endValue.value;
          break;
      }

      _.forEach(self.selection, function (lane) {
        var currentLaneNumber = getLaneCodeValue(lane);

        var properties = _.filter(self.getValue(currentLaneNumber), function(property){ return property.publicId !== currentPropertyValue.publicId; });
        properties.push(currentPropertyValue);
        self.setValue(currentLaneNumber, {properties: properties});
      });
    };

    self.getValue = function(laneNumber, marker) {
      return getProperty(self.getLane(laneNumber, marker), 'properties');
    };

    this.setNewLane = function(laneNumber) {
      var newLane;
      if(laneNumber.toString()[1] == 2){
        newLane = _.cloneDeep(self.getLane(laneNumber-1));
      }else{
        newLane = _.cloneDeep(self.getLane(laneNumber-2));
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
      self.selection.push(newLane);
      self.dirty = true;
    };

    function getLaneIndex(laneNumber, marker) {
      return _.findIndex(self.selection, function (lane) {
        return (_.isEmpty(marker) || lane.marker == marker) && _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && _.head(property.values).value == laneNumber;
        });
      });
    }

    this.removeLane = function(laneNumber, marker) {
      var laneIndex = getLaneIndex(laneNumber, marker);
      var removeLane = self.selection.splice(laneIndex,1)[0];
      removeLane.isDeleted = true;

      if(removeLane.id !== 0)
        assetsToBeRemoved.push(removeLane);

      reorganizeLanes(laneNumber);
      self.dirty = true;
    };

    this.expireLane = function(laneNumber, marker) {
      var laneIndex = getLaneIndex(laneNumber, marker);
      var expireLane = self.selection.splice(laneIndex,1)[0];
      expireLane.isExpired = true;
      assetsToBeExpired.push(expireLane);

      reorganizeLanes(laneNumber);
      self.dirty = true;
    };

    self.setValue = function(laneNumber, value, marker) {
      var laneIndex = getLaneIndex(laneNumber, marker);
      var newGroup = _.assign([], self.selection[laneIndex].properties, value);
      if(!self.dirty && _.isEqual(self.selection[laneIndex].properties, newGroup.properties)){
        self.dirty = false;
      }else{
        self.selection[laneIndex].properties = newGroup.properties;
        self.dirty = true;
      }
      eventbus.trigger(self.singleElementEvent('valueChanged'), self, laneNumber);
    };

    self.removeValue = function(laneNumber, marker) {
      self.setValue(laneNumber, undefined, marker);
    };

    self.isSelected = function(roadLink) {
      return _.some(linksSelected, function(link) {
        return self.isEqual(roadLink, link);
      });
    };
  };
})(this);
