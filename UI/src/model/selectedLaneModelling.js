(function(root) {
  root.SelectedLaneModelling = function(backend, collection, typeId, singleElementEventCategory, multiElementEventCategory, isSeparableAssetType) {
    SelectedLinearAsset.call(this, backend, collection, typeId, singleElementEventCategory, multiElementEventCategory, isSeparableAssetType);
    this.promotionDirty = false;
    var lanesFetched = [];
    var selectedRoadLink = null;
    var assetsToBeExpired = [];
    var self = this;
    var linksSelected = null;
    var currentLane;

    var roadNumber;
    var startRoadPartNumber;
    var startDistance;
    var endRoadPartNumber;
    var endDistance;
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

    this.isPromotionDirty = function () { return this.promotionDirty; };

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

      var lanesSortedByEndMeasure = _.sortBy(lanes, function(lane) {
        return lane.endMeasure;
      });

      _.forEach(laneCodesToPutMarkers, function (laneCode) {
        var characterCounterForLaneMarker = 0;
        for (var i = 0; i < lanesSortedByEndMeasure.length; i++) {
          if (laneCode == getLaneCodeValue(lanesSortedByEndMeasure[i]).toString()) {
            //The integer value of 'A' is 65, so every increment in the counter gives the next letter of the alphabet.
            lanesSortedByEndMeasure[i].marker = String.fromCharCode(characterCounterForLaneMarker + 65);
            characterCounterForLaneMarker += 1;
          }
        }
      });

      return lanesSortedByEndMeasure;
    };

    //Outer lanes that are expired are to be considered, the other are updates so we need to take those out
    //Here a outer lane is a lane with lane code that existed in the original but not in the modified configuration
    function omitIrrelevantExpiredLanes() {
      var lanesToBeRemovedFromExpire = _.filter(assetsToBeExpired, function (lane) {
        return !self.isOuterLane(getLaneCodeValue(lane));
      });

      _.forEach(lanesToBeRemovedFromExpire, function (lane) {
        _.remove(assetsToBeExpired, {'id': lane.id});
      });
    }

    self.splitLinearAsset = function(laneNumber, split, laneMarker) {
      collection.splitLinearAsset(self.getLane(laneNumber, laneMarker), split, function(splitLinearAssets) {
        var laneIndex = getLaneIndex(laneNumber, laneMarker);
        self.selection.splice(laneIndex,1);
        self.selection.push(splitLinearAssets.created, splitLinearAssets.existing);
        self.selection = giveSplitMarkers(self.selection);
        self.dirty = true;
        eventbus.trigger('laneModellingForm: reload');
      });
    };

    self.getSelectedLanes = function (lane){
      return collection.getGroup(lane);
    };

    self.open = function(linearAsset, singleLinkSelect) {
      self.close();
      var linearAssets = singleLinkSelect ? [linearAsset] : collection.getGroup(linearAsset);
      selectedRoadLink = linearAsset;
      backend.getLanesByLinkIdAndSidecode(linearAsset.linkId, linearAsset.sideCode, function(asset) {
        _.forEach(asset, function (lane) {
          lane.linkIds = _.map(linearAssets, function (linearAsset) {
            return linearAsset.linkId;
          });
          lane.selectedLinks = _.uniq(linearAssets);
        });
        var lanesWithSplitMarkers = giveSplitMarkers(asset);
        self.selection = lanesWithSplitMarkers;
        lanesFetched = _.cloneDeep(lanesWithSplitMarkers);
        linksSelected = linearAssets;
        collection.setSelection(self);
        assetsToBeExpired=[];
        eventbus.trigger(self.singleElementEvent('selected'), self);
      });
    };

    this.getSelectedRoadLink = function() {
      return selectedRoadLink;
    };

    this.setInitialRoadFields = function(){
      roadNumber = selectedRoadLink.roadNumber;
      startRoadPartNumber = Math.min.apply(null, _.compact(Property.pickUniqueValues(linksSelected, 'roadPartNumber')));
      startDistance = Math.min.apply(null, Property.chainValuesByPublicIdAndRoadPartNumber(linksSelected, startRoadPartNumber, 'startAddrMValue'));
      track = selectedRoadLink.track;

      var startRoadPartNumberElement  = {publicId: "startRoadPartNumber", propertyType: "number", required: 'required', values: [{value: startRoadPartNumber}]};
      var startDistanceElement = {publicId: "startDistance", propertyType: "number", required: 'required', values: [{value: startDistance}]};
      var endRoadPartNumberElement = {publicId: "endRoadPartNumber", propertyType: "number", required: 'required', values: [{value: ''}]};
      var endDistanceElement = {publicId: "endDistance", propertyType: "number", required: 'required', values: [{value: ''}]};

      _.forEach(self.selection, function (lane) {
        lane.properties.push(startRoadPartNumberElement, startDistanceElement, endRoadPartNumberElement, endDistanceElement);
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

    this.isLaneFullLinkLength = function(lane)  {
      var selectedMainLane = self.getLane(1);
      return lane.startMeasure !==  selectedMainLane.startMeasure || lane.endMeasure !== selectedMainLane.endMeasure;
    };

    this.haveNewLane = function () {
      return _.some(self.selection, function(lane){
        return lane.id === 0;
      });
    };

    this.isAddByRoadAddress = function() {
      var lane = _.find(self.selection, function (lane){
        return _.find(lane.properties, function (property) {
          return property.publicId == "startRoadPartNumber";
        });
      });

      return !_.isUndefined(lane);
    };

    self.lanesCutAreEqual = function() {
      var laneNumbers = _.map(self.selection, getLaneCodeValue);
      var cutLaneNumbers = _.transform(_.countBy(laneNumbers), function(result, count, value) {
        if (count > 1) result.push(value);
      }, []);

      return _.some(cutLaneNumbers, function (laneNumber){
        var lanes = _.filter(self.selection, function (lane){
          return _.find(lane.properties, function (property) {
            return property.publicId == "lane_code" && _.head(property.values).value == laneNumber;
          });
        });
        var sortedLanes = _.sortBy(lanes, function (lane) {
          return lane.endMeasure;
        });
        for (var i = 1; i < sortedLanes.length; i++) {
          if (_.isEqual(sortedLanes[i - 1].properties, sortedLanes[i].properties)) {
            return true;
          }
        }
        return false;
      });
    };

    this.isOuterLane= function(laneNumber) {
      return _.isUndefined(self.getLane(parseInt(laneNumber) + 2));
    };

    function omitUnrelevantProperties(lanes){
      return _.map(lanes, function (lane) {
        var laneWithoutUnrelevantInfo = _.omit(lane, ['linkId', 'linkIds', 'sideCode', 'selectedLinks', 'points', 'marker']);
        laneWithoutUnrelevantInfo.properties = _.filter(laneWithoutUnrelevantInfo.properties, function (prop) {
          return !_.includes(['startRoadPartNumber', 'startDistance', 'endRoadPartNumber', 'endDistance'], prop.publicId);
        });
        return laneWithoutUnrelevantInfo;
      });
    }

    function getSideCodesForLinks() {
      var selectedMainLane = _.find(self.selection, function (lane) {
        return getLaneCodeValue(lane) == 1;
      });
      var mainLaneGroup = collection.getGroup(selectedMainLane);
      var sideCodesMapped = mainLaneGroup.map(function(lane){
        return {linkId: lane.linkId, sideCode: lane.sideCode};
      });

      return sideCodesMapped;
    }

    function useOriginalLaneCode(lanes) {
      return _.map(lanes, function(lane){
        if(lane.originalLaneCode) {
          lane.newLaneCode = getLaneCodeValue(lane);
          setPropertyByPublicId(lane, "lane_code", lane.originalLaneCode);
          return lane;
        }
          else return lane;
      });

    }

    self.save = function(isAddByRoadAddressActive) {
      eventbus.trigger(self.singleElementEvent('saving'));
      omitIrrelevantExpiredLanes();

      var linkIds = _.head(self.selection).linkIds;
      var sideCode = _.head(self.selection).sideCode;
      var sideCodesForLinks = getSideCodesForLinks();

      var lanesWithOriginalLaneCode = useOriginalLaneCode(self.selection);
      var lanes = omitUnrelevantProperties(lanesWithOriginalLaneCode);

      var payload;
      if(isAddByRoadAddressActive) {
        payload = {
          sideCode: sideCode,
          laneRoadAddressInfo:{
            roadNumber: roadNumber,
            startRoadPart: parseInt(startRoadPartNumber),
            startDistance: parseInt(startDistance),
            endRoadPart: parseInt(endRoadPartNumber),
            endDistance: parseInt(endDistance),
            track: track
          },
          lanes: lanes
        };
      }else{
        payload = {
          linkIds: linkIds,
          sideCode: sideCode,
          sideCodesForLinks: sideCodesForLinks,
          lanes: lanes.concat(omitUnrelevantProperties(assetsToBeExpired))
        };
      }

      var backendOperation = isAddByRoadAddressActive ? backend.updateLaneAssetsByRoadAddress : backend.updateLaneAssets;

      backendOperation(payload, function() {
        self.dirty = false;
        self.promotionDirty = false;
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
      self.promotionDirty = false;
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

    this.setAddressesValues = function(currentPropertyValue) {
      var propertyValue = _.head(currentPropertyValue.values);
      var value = _.isEmpty(propertyValue) ? propertyValue : propertyValue.value;

      switch(currentPropertyValue.publicId) {
        case "startRoadPartNumber":
          startRoadPartNumber = value;
          break;
        case "startDistance":
          startDistance = value;
          break;
        case "endRoadPartNumber":
          endRoadPartNumber = value;
          break;
        case "endDistance":
          endDistance = value;
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

    // Calculate accurate road address start and end m-values for additional lane. Get road link measures from main lane.
    this.getAddressValuesForCutLane = function(lane) {
      var mainLane = collection.getMainLaneByLinkIdAndSideCode(lane.linkId, lane.sideCode);
      var roadLinkLength = mainLane.endMeasure;
      var roadAddressSideCode = mainLane.roadAddressSideCode;

      // A coefficient is needed because road address and geometry lengths don't match exactly. Coefficient tells
      // how long is '1 road address meter' on current link's geometry
      var coefficient = (mainLane.endAddrMValue - mainLane.startAddrMValue) / roadLinkLength;

      // If road address side code is towards digitizing(2) startAddrMValue points to lane's start measure (southern end-point)
      // and endAddrMValue points to lane's end measure (northern  end-point)
      if (roadAddressSideCode === 2) {
        lane.startAddrMValue = mainLane.startAddrMValue + Math.round(lane.startMeasure * coefficient);
        lane.endAddrMValue = mainLane.startAddrMValue + Math.round(lane.endMeasure * coefficient);
      }
      // If road address side code is against digitizing(3) startAddrMValue points to lane's  end measure (northern end-point)
      // and endAddrMValue points to lane's start measure (southern point)
      else if (roadAddressSideCode === 3) {
        lane.endAddrMValue = mainLane.endAddrMValue - Math.round(lane.startMeasure * coefficient);
        lane.startAddrMValue = mainLane.endAddrMValue - Math.round(lane.endMeasure * coefficient);
      }

      return lane;
    };

    this.setNewLane = function(laneNumber) {
      var laneToClone;
      if(laneNumber == 2){
        laneToClone = self.getLane(laneNumber-1);
      }else{
        laneToClone = self.getLane(laneNumber-2);
      }

      var newLane = _.cloneDeep(_.omit(laneToClone, ['marker', 'createdBy', 'createdAt', 'modifiedBy', 'modifiedAt', 'properties']));

      var outerLaneIsMainLane = laneNumber == 2 || laneNumber == 3;

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
      self.selection.splice(laneIndex,1);

      reorganizeLanes(laneNumber);
      self.dirty = true;
    };

    this.promoteToMainLane = function(laneNumber) {
      var lanesOnLink = self.selection;
      var lanesWithOrderNumbers = self.getOrderingNumbers(lanesOnLink);
      var newMainLane = _.find(lanesWithOrderNumbers, function(lane) {
        return getLaneCodeValue(lane) === laneNumber;
      });

      var lanesAfterPromotion = laneCodesAfterPromotion(lanesWithOrderNumbers, newMainLane);
      _.forEach(lanesAfterPromotion, function (lane) {
        setPropertyByPublicId(lane, 'lane_type', null);
      });
      removePropertyByPublicId(newMainLane, "end_date");
      self.selection = lanesAfterPromotion;
      self.dirty = true;
      self.promotionDirty = true;
    };

    var laneCodesAfterPromotion = function (lanesWithOrderNumbers, newMainLane) {
      var newMainLaneOrderNo = newMainLane.orderNo;
      lanesWithOrderNumbers.forEach(function (lane) {
        lane.originalLaneCode = getLaneCodeValue(lane);
        var difference = newMainLaneOrderNo - lane.orderNo;
        if (difference > 0) {
          setPropertyByPublicId(lane, 'lane_code', difference * 2);
        } else {
          setPropertyByPublicId(lane, 'lane_code', Math.abs(difference) * 2 + 1);
        }
      });

      return lanesWithOrderNumbers;
    };

    function setPropertyByPublicId(lane, propertyPublicId, propertyValue) {
      _.map(lane.properties, function (prop) {
        if (prop.publicId === propertyPublicId) {
          prop.values[0] = {value: propertyValue};
        }
      });
    }

    function removePropertyByPublicId(lane, propertyPublicId) {
      _.map(lane.properties, function (prop) {
        if (prop.publicId === propertyPublicId) {
          prop.values = undefined;
        }
      });
    }

    this.getOrderingNumbers = function(lanesOnLink) {
      var leftAndRightLanesPartitioned = _.partition(lanesOnLink, function(lane) {
        var laneCode = getLaneCodeValue(lane);
        return laneCode % 2 === 0;
      });

      var leftLanes = _.head(leftAndRightLanesPartitioned);
      var rightLanes = _.last(leftAndRightLanesPartitioned);

      var leftLanesOrdered = _.orderBy(leftLanes, function(lane) {
        return getLaneCodeValue(lane);
      }, 'desc');

      var rightLanesOrdered = _.orderBy(rightLanes, function(lane) {
        return getLaneCodeValue(lane);
      }, 'asc');
      var lanesLeftToRight = leftLanesOrdered.concat(rightLanesOrdered);

      var orderNumberCounter = 0;
      lanesLeftToRight.forEach(function (lane) {
        var laneCode = getLaneCodeValue(lane);
        var previousLane = lanesLeftToRight[lanesLeftToRight.indexOf(lane) - 1];
        if(previousLane){
          if(getLaneCodeValue(previousLane) == laneCode) {
            lane.orderNo = orderNumberCounter;
          }
          else {
            lane.orderNo = orderNumberCounter += 1;
          }
        }
        else {
          lane.orderNo = orderNumberCounter += 1;
        }
      });

      return lanesLeftToRight;
    };

    this.expireLane = function(laneNumber, marker) {
      var laneIndex = getLaneIndex(laneNumber, marker);
      var expiredLane = self.selection.splice(laneIndex,1)[0];

      //expiredLane could be modified by the user so we need to fetch the original
      var originalExpiredLane = _.find(lanesFetched, {'id': expiredLane.id});
      if (linksSelected.length > 1 && _.isUndefined(marker)) {
        var expiredGroup = collection.getGroup(originalExpiredLane);
        expiredGroup.forEach(function (lane) {
          lane.isExpired = true;
          assetsToBeExpired.push(lane);
        });
      } else {
        originalExpiredLane.isExpired = true;
        assetsToBeExpired.push(originalExpiredLane);
      }
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
