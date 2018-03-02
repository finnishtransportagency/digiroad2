(function(root) {
  root.SelectedLinkProperty = function(backend, roadCollection) {
    var current = [];
    var dirty = false;
    var targets = [];
    var sources = [];
    var featuresToKeep = [];
    var previousAdjacents = [];
    var floatingRoadMarker = [];
    var anomalousMarkers = [];
    var BAD_REQUEST = 400;
    var UNAUTHORIZED_401 = 401;
    var PRECONDITION_FAILED_412 = 412;
    var INTERNAL_SERVER_ERROR_500 = 500;
    var floatingRoadLinkType=-1;
    var noAnomaly=0;
    var noAddressAnomaly=1;
    var geometryChangedAnomaly=2;

    var markers = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
      "AA", "AB", "AC", "AD", "AE", "AF", "AG", "AH", "AI", "AJ", "AK", "AL", "AM", "AN", "AO", "AP", "AQ", "AR", "AS", "AT", "AU", "AV", "AW", "AX", "AY", "AZ",
      "BA", "BB", "BC", "BD", "BE", "BF", "BG", "BH", "BI", "BJ", "BK", "BL", "BM", "BN", "BO", "BP", "BQ", "BR", "BS", "BT", "BU", "BV", "BW", "BX", "BY", "BZ",
      "CA", "CB", "CC", "CD", "CE", "CF", "CG", "CH", "CI", "CJ", "CK", "CL", "CM", "CN", "CO", "CP", "CQ", "CR", "CS", "CT", "CU", "CV", "CW", "CX", "CY", "CZ"];

    var close = function() {
      if (!_.isEmpty(current) && !isDirty()) {
        _.forEach(current, function(selected) { selected.unselect(); });
        applicationModel.setActiveButtons(false);
        eventbus.trigger('layer:enableButtons', true);
        eventbus.trigger('linkProperties:unselected');
        current = [];
        sources = [];
        targets = [];
        dirty = false;
        featuresToKeep = [];
        applicationModel.setActiveButtons(false);
      }
    };

    var isSingleLinkSelection = function() {
      return current.length === 1;
    };

    var isDifferingSelection = function(singleLinkSelect) {
      return (!_.isUndefined(singleLinkSelect) &&
      (singleLinkSelect !== isSingleLinkSelection()));
    };

    var setCurrent = function(data){
      current = data;
    };

    var extractDataForDisplay = function(selectedData) {
      var extractUniqueValues = function(selectedData, property) {
        return _.chain(selectedData)
          .pluck(property)
          .uniq()
          .value()
          .join(', ');
      };

      var properties = _.cloneDeep(_.first(selectedData));
      var isMultiSelect = selectedData.length > 1;
      if (isMultiSelect) {
        var ambiguousFields = ['maxAddressNumberLeft', 'maxAddressNumberRight', 'minAddressNumberLeft', 'minAddressNumberRight',
          'municipalityCode', 'verticalLevel', 'roadNameFi', 'roadNameSe', 'roadNameSm', 'modifiedAt', 'modifiedBy',
          'endDate'];
        properties = _.omit(properties, ambiguousFields);
        var latestModified = dateutil.extractLatestModifications(selectedData);
        var municipalityCodes = {municipalityCode: extractUniqueValues(selectedData, 'municipalityCode')};
        var verticalLevels = {verticalLevel: extractUniqueValues(selectedData, 'verticalLevel')};
        var roadPartNumbers = {roadPartNumber: extractUniqueValues(selectedData, 'roadPartNumber')};
        var elyCodes = {elyCode: extractUniqueValues(selectedData, 'elyCode')};
        var trackCode = {trackCode: extractUniqueValues(selectedData, 'trackCode')};
        var discontinuity = {discontinuity: extractUniqueValues(selectedData, 'discontinuity')};
        var startAddressM = {startAddressM: _.min(_.chain(selectedData).pluck('startAddressM').uniq().value())};
        var endAddressM = {endAddressM: _.max(_.chain(selectedData).pluck('endAddressM').uniq().value())};
        var roadLinkSource = {roadLinkSource: extractUniqueValues(selectedData, 'roadLinkSource')};

        var roadNames = {
          roadNameFi: extractUniqueValues(selectedData, 'roadNameFi'),
          roadNameSe: extractUniqueValues(selectedData, 'roadNameSe'),
          roadNameSm: extractUniqueValues(selectedData, 'roadNameSm')
        };
        _.merge(properties, latestModified, municipalityCodes, verticalLevels, roadPartNumbers, roadNames, elyCodes, startAddressM, endAddressM);
      }
      return properties;
    };

    var open = function(linkId, id, singleLinkSelect, visibleFeatures, isSuravage) {
      var canIOpen = !_.isUndefined(linkId) ? !isSelectedByLinkId(linkId) || isDifferingSelection(singleLinkSelect) : !isSelectedById(id) || isDifferingSelection(singleLinkSelect);
      if (canIOpen) {
        if(isSuravage){
          if(!_.isUndefined(linkId)){
            current = singleLinkSelect ? roadCollection.getSuravageByLinkId([linkId]) : roadCollection.getSuravageGroupByLinkId(linkId);
          } else {
            current = singleLinkSelect ? roadCollection.getSuravageById([id]) : roadCollection.getSuravageGroupById(id);
          }
        } else {
          if(!_.isUndefined(linkId)){
            current = singleLinkSelect ? roadCollection.getByLinkId([linkId]) : roadCollection.getGroupByLinkId(linkId);
          } else {
            current = singleLinkSelect ? roadCollection.getById([id]) : roadCollection.getGroupById(id);
          }
        }

        var currentFloatings = getCurrentFloatings();
        if(!_.isEmpty(currentFloatings)){
          setSources(currentFloatings);
        }

        _.forEach(current, function (selected) {
          selected.select();
        });
        processOl3Features(visibleFeatures);
        eventbus.trigger('linkProperties:selected', extractDataForDisplay(get()));
      }
    };

    var openFloating = function(linkId, id, visibleFeatures){
      var canIOpen = !_.isUndefined(linkId) ? !isSelectedByLinkId(linkId)  : !isSelectedById(id);
      if (canIOpen) {
        applicationModel.toggleSelectionTypeFloating();
        if(!_.isUndefined(linkId)){
          current = roadCollection.getGroupByLinkId(linkId);
        } else {
          current = roadCollection.getGroupById(id);
        }

        var currentFloatings = getCurrentFloatings();
        if(!_.isEmpty(currentFloatings)){
          setSources(currentFloatings);
        }
        //Segment to construct adjacency
        fillAdjacents(linkId);

        var data4Display = _.map(get(), function(feature){
          return extractDataForDisplay([feature]);
        });

        if(!applicationModel.isReadOnly() && get()[0] && get()[0].roadLinkType === floatingRoadLinkType){
          addToFeaturesToKeep(data4Display);
        }
        if(!_.isEmpty(featuresToKeep) && !isLinkIdInFeaturesToKeep(linkId)){
          addToFeaturesToKeep(data4Display);
        }
        processOl3Features(visibleFeatures);
        eventbus.trigger('adjacents:startedFloatingTransfer');
        eventbus.trigger('linkProperties:selected', data4Display);
        eventbus.trigger('linkProperties:deactivateInteractions');
      }
    };

    var openUnknown = function(linkId, id, visibleFeatures) {
      var canIOpen = !_.isUndefined(linkId) ? true : !isSelectedById(id);
      if (canIOpen) {
        if(featuresToKeep.length === 0){
          close();
        } else {
          if (!_.isEmpty(current) && !isDirty()) {
            _.forEach(current, function(selected) { selected.unselect(); });
          }
        }
        if(!_.isUndefined(linkId)){
          current = _.uniq(roadCollection.getByLinkId([linkId]), _.isEqual);
        } else {
          current = _.uniq(roadCollection.getById([id]), _.isEqual);
        }
        if(current[0].getData().anomaly == geometryChangedAnomaly) {
          current = _.filter(roadCollection.getTmpRoadLinkGroups(), function(linkGroup){
            return linkGroup.getData().linkId === linkId;
          });
        }

        eventbus.trigger('linkProperties:activateAllSelections');

        _.forEach(current, function (selected) {
          selected.select();
        });

        var currentFloatings = _.filter(current, function(curr){
          return curr.getData().roadLinkType === floatingRoadLinkType;
        });
        if(!_.isEmpty(currentFloatings)){
          setSources(currentFloatings);
        }

        var data4Display = _.map(get(), function(feature){
          return extractDataForDisplay([feature]);
        });

        if(!applicationModel.isReadOnly() && get()[0].anomaly === noAddressAnomaly){
          addToFeaturesToKeep(data4Display);
        }
        if(!_.isEmpty(featuresToKeep) && !isLinkIdInFeaturesToKeep(linkId)){
          addToFeaturesToKeep(data4Display);
        }
        var contains = _.find(featuresToKeep, function(fk){
          return fk.linkId === linkId;
        });

        if(!_.isEmpty(featuresToKeep) && _.isUndefined(contains)){
          if(_.isArray(extractDataForDisplay(get()))){
            featuresToKeep = featuresToKeep.concat(data4Display);
          } else {
            addToFeaturesToKeep(data4Display);
          }
        }
        processOl3Features(visibleFeatures);
        eventbus.trigger('adjacents:startedFloatingTransfer');
        eventbus.trigger('linkProperties:selected', data4Display);
        _.defer(function(){
          eventbus.trigger('linkProperties:deactivateAllSelections');
        });
      }
    };

    var processOl3Features = function (visibleFeatures){
      var selectedOL3Features = _.filter(visibleFeatures, function(vf){
        return (_.some(get().concat(featuresToKeep), function(s){
            return s.linkId === vf.roadLinkData.linkId;
          })) && (_.some(get().concat(featuresToKeep), function(s){
            return s.mmlId === vf.roadLinkData.mmlId;
          }));
      });
      eventbus.trigger('linkProperties:ol3Selected', selectedOL3Features);
    };

    var fillAdjacents = function(linkId){
      var orderedCurrent = _.sortBy(current, function(curr){
        return curr.getData().endAddressM;
      });
      var previous = _.first(orderedCurrent);
      var areAdjacent = true;
      //Quick Check to find if the features in the group are all adjacent
      _.forEach(_.rest(orderedCurrent),function (oc){
        areAdjacent = areAdjacent && GeometryUtils.areAdjacents(previous.getPoints(),oc.getPoints());
        previous = oc;
      });
      //If they are then no change to the current is needed, however if they aren't then we need to discover the adjacent network and put that as the current.
      if(!areAdjacent) {
        var adjacentNetwork = [];
        var selectedFeature = _.find(orderedCurrent, function(oc){
          return oc.getData().linkId === linkId;
        });
        var selectedFeatureIndex = _.findIndex(orderedCurrent, function(oc){
          return oc.getData().linkId === linkId;
        });
        //We get the all the roads until the clicked target
        var firstPart = orderedCurrent.slice(0, selectedFeatureIndex);
        //Since the clicked target is not included in the slice we need to add it to the head
        firstPart.push(selectedFeature);
        //Then we get the roads from the clicked target to the finish
        var rest = orderedCurrent.slice(selectedFeatureIndex);
        previous = _.last(firstPart);
        //we put the clicked target in the network
        adjacentNetwork = adjacentNetwork.concat(previous);
        //Then we keep adding to the network until we find the break in adjacency, terminating the cycle
        for(var i = firstPart.length-2; i >= 0; i--) {
          if(GeometryUtils.areAdjacents(firstPart[i].getPoints(), previous.getPoints())){
            adjacentNetwork.push(firstPart[i]);
            previous = firstPart[i];
          } else {
            i = -1;
          }
        }
        previous = _.first(rest);
        //Same logic as prior but since in this part the clicked target is the in the beginning we just look forward
        for(var j = 1; j < rest.length; j++) {
          if(GeometryUtils.areAdjacents(rest[j].getPoints(),previous.getPoints())){
            adjacentNetwork.push(rest[j]);
            previous = rest[j];
          }
          else {
            j = rest.length +1;
          }
        }
        //Now we just tidy up the adjacentNetwork by endAddressM again and set the current to this
        applicationModel.setContinueButton(false);
        current = _.sortBy(adjacentNetwork, function(curr){
          return curr.getData().endAddressM;
        });
      }
      applicationModel.setContinueButton(false);
    };

    var getLinkAdjacents = function(link) {
      var linkIds = {};
      var chainLinks = [];
      _.each(current, function (link) {
        if (!_.isUndefined(link))
          chainLinks.push(link.getData().linkId);
      });
      _.each(targets, function (link) {
        chainLinks.push(link.linkId);
      });
      var data = {
        "selectedLinks": _.uniq(chainLinks), "linkId": parseInt(link.linkId), "roadNumber": parseInt(link.roadNumber),
        "roadPartNumber": parseInt(link.roadPartNumber), "trackCode": parseInt(link.trackCode)
      };

      if (!applicationModel.isReadOnly() && applicationModel.getSelectionType() !== 'all'){
        applicationModel.addSpinner();
        backend.getTargetAdjacent(data, function (adjacents) {
          applicationModel.removeSpinner();
          if (!_.isEmpty(adjacents)){
            linkIds = adjacents;
          }
          applicationModel.setCurrentAction(applicationModel.actionCalculating);
          if (!applicationModel.isReadOnly()) {
            var rejectedRoads = _.reject(get().concat(featuresToKeep), function(link){
              return link.segmentId === "" || link.anomaly === geometryChangedAnomaly;
            });
            var selectedLinkIds = _.map(rejectedRoads, function (roads) {
              return roads.linkId;
            });
            var filteredPreviousAdjacents = _.filter(adjacents, function(adj){
              return !_.contains(_.pluck(previousAdjacents, 'linkId'), adj.linkId);
            }).concat(previousAdjacents);
            var filteredAdjacents = _.filter(filteredPreviousAdjacents, function(prvAdj){
              return !_.contains(selectedLinkIds, prvAdj.linkId);
            });
            previousAdjacents = filteredAdjacents;
            var markedRoads = {
              "adjacents": _.map(applicationModel.getSelectionType() === 'floating' ? _.reject(filteredAdjacents, function(t){
                return t.roadLinkType != floatingRoadLinkType;
              }) :filteredAdjacents, function (a, index) {
                return _.merge({}, a, {"marker": markers[index]});
              }), "links": link
            };
            if(applicationModel.getSelectionType() === 'floating') {
              eventbus.trigger("adjacents:floatingAdded", markedRoads.adjacents);
              if(_.isEmpty(markedRoads.adjacents)){
                applicationModel.setContinueButton(true);
              }
            }
            else {
              eventbus.trigger("adjacents:added", markedRoads.links, markedRoads.adjacents);
            }
            if(applicationModel.getSelectionType() !== 'unknown'){
              eventbus.trigger('adjacents:startedFloatingTransfer');
            }
          }
        });
      }
      return linkIds;
    };

    var getLinkFloatingAdjacents = function(link) {
      var linkIds = {};
      var chainLinks = [];
      _.each(current, function (link) {
        if (!_.isUndefined(link))
          chainLinks.push(link.getData().linkId);
      });
      _.each(targets, function (link) {
        chainLinks.push(link.linkId);
      });
      var data = {
        "selectedLinks": _.uniq(chainLinks), "linkId": parseInt(link.linkId), "roadNumber": parseInt(link.roadNumber),
        "roadPartNumber": parseInt(link.roadPartNumber), "trackCode": parseInt(link.trackCode)
      };

      if (!applicationModel.isReadOnly() && applicationModel.getSelectionType() !== 'all'){
        applicationModel.addSpinner();
        backend.getFloatingAdjacent(data, function (adjacents) {
          applicationModel.removeSpinner();
          if (!_.isEmpty(adjacents)){
            linkIds = adjacents;
          }
          applicationModel.setCurrentAction(applicationModel.actionCalculating);
          if (!applicationModel.isReadOnly()) {
            var rejectedRoads = _.reject(get().concat(featuresToKeep), function(link){
              return link.segmentId === "" || link.anomaly === geometryChangedAnomaly;
            });
            var selectedLinkIds = _.map(rejectedRoads, function (roads) {
              return roads.linkId;
            });
            var filteredPreviousAdjacents = _.filter(adjacents, function(adj){
              return !_.contains(_.pluck(previousAdjacents, 'linkId'), adj.linkId);
            }).concat(previousAdjacents);
            var filteredAdjacents = _.filter(filteredPreviousAdjacents, function(prvAdj){
              return !_.contains(selectedLinkIds, prvAdj.linkId);
            });
            previousAdjacents = filteredAdjacents;
            var markedRoads = {
              "adjacents": _.map(applicationModel.getSelectionType() === 'floating' ? _.reject(filteredAdjacents, function(t){
                return t.roadLinkType != floatingRoadLinkType;
              }) :filteredAdjacents, function (a, index) {
                return _.merge({}, a, {"marker": markers[index]});
              }), "links": link
            };
            if(applicationModel.getSelectionType() === 'floating') {
              eventbus.trigger("adjacents:floatingAdded", markedRoads.adjacents);
              if(_.isEmpty(markedRoads.adjacents)){
                applicationModel.setContinueButton(true);
              }
            }
            else {
              eventbus.trigger("adjacents:added", markedRoads.links, markedRoads.adjacents);
            }
            if(applicationModel.getSelectionType() !== 'unknown'){
              eventbus.trigger('adjacents:startedFloatingTransfer');
            }
          }
        });
      }
      return linkIds;
    };

    eventbus.on("adjacents:additionalSourceSelected", function(existingSources, additionalSourceLinkId) {
      sources = current;
      var fetchedFeature = roadCollection.getRoadLinkByLinkId(parseInt(additionalSourceLinkId));
      if(!_.isUndefined(fetchedFeature)){
        sources.push(fetchedFeature);
        featuresToKeep.push(fetchedFeature.getData());
      }
      var chainLinks = [];
      _.each(sources, function(link){
        if(!_.isUndefined(link))
          chainLinks.push(link.getData().linkId);
      });
      _.each(targets, function(link){
        chainLinks.push(link.getData().linkId);
      });
      var newSources = _.isArray(existingSources) ? existingSources : [existingSources];
      if(!_.isUndefined(additionalSourceLinkId) && !_.isUndefined(fetchedFeature))
        newSources.push(fetchedFeature.getData());
      var data = _.map(newSources, function (ns){
        return {"selectedLinks": _.uniq(chainLinks), "linkId": parseInt(ns.linkId), "roadNumber": parseInt(ns.roadNumber),
          "roadPartNumber": parseInt(ns.roadPartNumber), "trackCode": parseInt(ns.trackCode)};
      });
      backend.getAdjacentsFromMultipleSources(data, function(adjacents){
        if(!_.isEmpty(adjacents) && !applicationModel.isReadOnly()){
          var calculatedRoads = {"adjacents" : _.map(adjacents, function(a, index){
            return _.merge({}, a, {"marker": markers[index]});
          }), "links": newSources};
          eventbus.trigger("adjacents:aditionalSourceFound",calculatedRoads.links, calculatedRoads.adjacents );
        }
      });
    });

    eventbus.on('linkProperties:saved', function(){
      eventbus.trigger('layer:enableButtons', true);
      applicationModel.toggleSelectionTypeAll();
      clearFeaturesToKeep();
    });

    var openMultiple = function(links) {
      var uniqueLinks = _.unique(links, 'linkId');
      current = roadCollection.get(_.pluck(uniqueLinks, 'linkId'));
      _.forEach(current, function (selected) {
        selected.select();
      });
      eventbus.trigger('linkProperties:multiSelected', extractDataForDisplay(get()));
    };

    var isDirty = function() {
      return dirty;
    };

    var isSelectedById = function(id) {
      return _.some(current, function(selected) {
        return selected.getData().id === id; });
    };

    var isSelectedByLinkId = function(linkId) {
      return _.some(current, function(selected) {
        return selected.getData().linkId === linkId; });
    };

    var transferringCalculation = function(){
      var targetsData = _.map(targets,function (t){
        if (_.isUndefined(t.linkId)) {
          return t.getData();
        } else return t;
      });

      var targetDataIds = _.uniq(_.filter(_.map(targetsData.concat(featuresToKeep), function(feature){
        if(feature.roadLinkType != floatingRoadLinkType && feature.anomaly == noAddressAnomaly){
          return feature.linkId.toString();
        }
      }), function (target){
        return !_.isUndefined(target);
      }));

      var sourceDataIds = _.filter(_.map(getSources(), function (feature) {
        if(feature.roadLinkType == floatingRoadLinkType){
          return feature.linkId.toString();
        }
      }), function (source){
        return !_.isUndefined(source);
      });
      var data = {"sourceLinkIds": _.uniq(sourceDataIds), "targetLinkIds":_.uniq(targetDataIds)};

      if(!_.isEmpty(data.sourceLinkIds) && !_.isEmpty(data.targetLinkIds)){
        backend.getTransferResult(data, function(result) {
          if(!_.isEmpty(result) && !applicationModel.isReadOnly()) {
            eventbus.trigger("adjacents:roadTransfer", result, sourceDataIds.concat(targetDataIds), targetDataIds);
            roadCollection.setNewTmpRoadAddresses(result);
            eventbus.trigger('linkProperties:cleanFloatingsAfterDefloat');
          }
        });
      } else {
        eventbus.trigger('linkProperties:transferFailed', PRECONDITION_FAILED_412);
      }
    };

    var saveTransfer = function() {
      eventbus.trigger('linkProperties:saving');
      var roadAddresses = roadCollection.getNewTmpRoadAddresses();

      var targetsData = _.map(targets,function (t){
        if(_.isUndefined(t.linkId)){
          return t.getData();
        }else return t;
      });

      var targetDataIds = _.uniq(_.filter(_.map(targetsData.concat(featuresToKeep), function(feature){
        if(feature.roadLinkType != floatingRoadLinkType && feature.anomaly == noAddressAnomaly){
          return feature.linkId;
        }
      }), function (target){
        return !_.isUndefined(target);
      }));
      var sourceDataIds = _.filter(_.map(get().concat(featuresToKeep), function (feature) {
        if(feature.roadLinkType == floatingRoadLinkType){
          return feature.linkId;
        }
      }), function (source){
        return !_.isUndefined(source);
      });

      var data = {'sourceIds': sourceDataIds, 'targetIds': targetDataIds};

      if(!_.isEmpty(data.sourceIds) && !_.isEmpty(data.targetIds)){
        backend.createRoadAddress(data, function(errorObject) {
          if (errorObject.status == INTERNAL_SERVER_ERROR_500 || errorObject.status == BAD_REQUEST) {
            eventbus.trigger('linkProperties:transferFailed', errorObject.status);
          }
        });
      } else {
        eventbus.trigger('linkProperties:transferFailed', PRECONDITION_FAILED_412);
      }
    };

    var addTargets = function(target, adjacents){
      if(!_.contains(targets,target))
        targets.push(roadCollection.getRoadLinkByLinkId(parseInt(target)).getData());
      var targetData = _.filter(adjacents, function(adjacent){
        return adjacent.linkId == target;
      });
      if(!_.isEmpty(targetData)){
        $('#aditionalSource').remove();
        $('#adjacentsData').remove();
        getLinkAdjacents(_.first(targetData));
      }
    };

    var getFloatingRoadMarker = function() {
      return floatingRoadMarker;
    };

    var setFloatingRoadMarker  = function(ft) {
      floatingRoadMarker = ft;
    };

    var getAnomalousMarkers = function(){
      return anomalousMarkers;
    };

    var setAnomalousMarkers = function(markers){
      anomalousMarkers = markers;
    };

    var getTargets = function(){
      return _.union(_.map(targets, function (roadLink) {
        return roadLink.getData();
      }));
    };

    var getSources = function() {
      return _.union(_.map(sources, function (roadLink) {
        return roadLink.getData();
      }));
    };

    var setSources = function(scs) {
      sources = scs;
    };

    var resetSources = function() {
      sources = [];
      return sources;
    };

    var resetTargets = function() {
      targets = [];
      return targets;
    };

    var setDirty = function(state){
      dirty = state;
    };

    var cancel = function() {
      dirty = false;
      _.each(current, function(selected) { selected.cancel(); });
      if(!_.isUndefined(_.first(current))){
        var originalData = _.first(current).getData();
        eventbus.trigger('linkProperties:cancelled', _.cloneDeep(originalData));
        eventbus.trigger('roadLinks:clearIndicators');
      }
    };

    var cancelAndReselect = function(action){
      if(action===applicationModel.actionCalculating){
        var floatingMarkers = getFloatingRoadMarker();
        eventbus.trigger('linkProperties:floatingRoadMarkerPreviousSelected', floatingMarkers);
      }
      clearAndReset(false);
      current = [];
      eventbus.trigger('linkProperties:clearHighlights');
    };

    var clearAndReset = function(afterDefloat){
      roadCollection.resetTmp();
      roadCollection.resetChangedIds();
      applicationModel.resetCurrentAction();
      applicationModel.setContinueButton(false);
      applicationModel.setActiveButtons(false);
      roadCollection.resetPreMovedRoadAddresses();
      clearFeaturesToKeep();
      eventbus.trigger('roadLinks:clearIndicators');
      if(!afterDefloat) {
        roadCollection.resetNewTmpRoadAddresses();
        resetSources();
        resetTargets();
        previousAdjacents = [];
      }
    };

    var cancelAfterDefloat = function(action, changedTargetIds) {
      dirty = false;
      var originalData = _.filter(featuresToKeep, function(feature){
        return feature.roadLinkType === floatingRoadLinkType;
      });
      if(action !== applicationModel.actionCalculated && action !== applicationModel.actionCalculating)
        clearFeaturesToKeep();
      if(_.isEmpty(changedTargetIds)) {
        clearAndReset(true);
        eventbus.trigger('linkProperties:selected', _.cloneDeep(originalData));
      }
      $('#adjacentsData').remove();
      if(applicationModel.isActiveButtons() || action === -1){
        if(action !== applicationModel.actionCalculated){
          applicationModel.setActiveButtons(false);
          eventbus.trigger('roadLinks:unSelectIndicators', originalData);
        }
        if (action){
          applicationModel.setContinueButton(false);
          eventbus.trigger('roadLinks:deleteSelection');
        }
        eventbus.trigger('roadLinks:fetched', action, changedTargetIds);
        applicationModel.setContinueButton(true);
      }
    };

    var setLinkProperty = function(key, value) {
      dirty = true;
      _.each(current, function(selected) { selected.setLinkProperty(key, value); });
      eventbus.trigger('linkProperties:changed');
    };
    var setTrafficDirection = _.partial(setLinkProperty, 'trafficDirection');
    var setFunctionalClass = _.partial(setLinkProperty, 'functionalClass');
    var setLinkType = _.partial(setLinkProperty, 'linkType');

    var get = function() {
      return _.map(current, function(roadLink) {
        return roadLink.getData();
      });
    };

    var getCurrentFloatings = function(){
      return _.filter(current, function(curr){
        return curr.getData().roadLinkType === floatingRoadLinkType;
      });
    };

    var getFeaturesToKeepFloatings = function() {
      return _.filter(featuresToKeep, function (fk) {
        return fk.roadLinkType === floatingRoadLinkType;
      });
    };

    var getFeaturesToKeepUnknown = function() {
      return _.filter(featuresToKeep, function (fk) {
        return fk.anomaly === noAddressAnomaly;
      });
    };

    var isLinkIdInCurrent = function(linkId){
      var currentLinkIds = _.map(current, function(curr){
        return curr.getData().linkId;
      });
      return _.contains(currentLinkIds, linkId);
    };

    var isLinkIdInFeaturesToKeep = function(linkId){
      var featuresToKeepLinkIds = _.map(featuresToKeep, function(fk){
        return fk.linkId;
      });
      return _.contains(featuresToKeepLinkIds, linkId);
    };

    var count = function() {
      return current.length;
    };

    var getFeaturesToKeep = function(){
      return featuresToKeep;
    };

    var addToFeaturesToKeep = function(data4Display){
      if(_.isArray(data4Display)){
        featuresToKeep = featuresToKeep.concat(data4Display);
      } else {
        featuresToKeep.push(data4Display);
      }
    };

    var clearFeaturesToKeep = function() {
      if('floating' === applicationModel.getSelectionType() || 'unknown' === applicationModel.getSelectionType()){
        featuresToKeep = _.filter(featuresToKeep, function(feature){
          return feature.roadLinkType === floatingRoadLinkType;
        });
      } else {
        featuresToKeep = [];
      }
    };

    var continueSelectUnknown = function() {
      if(!applicationModel.getContinueButtons()){
        new ModalConfirm("Tarkista irti geometriasta olevien tieosoitesegmenttien valinta. Kaikkia peräkkäisiä sopivia tieosoitesegmenttejä ei ole valittu.");
        return false;
      }else {
        return true;
      }
    };

    var featureExistsInSelection = function(checkMe){
      var linkIds = _.map(get(), function(feature){
        return feature.linkId;
      });
      var didIfindIt = _.find(linkIds,function (link) {
        return checkMe.data.linkId === link;
      });
      return !_.isUndefined(didIfindIt);
    };

    var isFloatingHomogeneous = function(floatingFeature) {
      var firstFloating = _.first(featuresToKeep);
      if(floatingFeature.data.roadPartNumber === parseInt(firstFloating.roadPartNumber) && floatingFeature.data.trackCode === firstFloating.trackCode && floatingFeature.data.roadNumber === firstFloating.roadNumber){
        return true;
      }else{
        return false;
      }
    };

    var filterFeaturesAfterSimulation = function(features){
      var linkIdsToRemove = linkIdsToExclude();
      if(applicationModel.getCurrentAction() === applicationModel.actionCalculated){
        //Filter the features without said linkIds
        if(linkIdsToRemove.length !== 0){
          return _.reject(features, function(feature){
            return _.contains(linkIdsToRemove, feature.roadLinkData.linkId);
          });
        } else {
          return features;
        }
      } else return features;
    };

    var linkIdsToExclude = function(){
      return _.chain(getFeaturesToKeepFloatings().concat(getFeaturesToKeepUnknown()).concat(getFeaturesToKeep())).map(function(feature){
        return feature.linkId;
      }).uniq().value();
    };

    return {
      getSources: getSources,
      setSources: setSources,
      resetSources: resetSources,
      addTargets: addTargets,
      getTargets: getTargets,
      resetTargets: resetTargets,
      getFeaturesToKeep: getFeaturesToKeep,
      addToFeaturesToKeep: addToFeaturesToKeep,
      clearFeaturesToKeep: clearFeaturesToKeep,
      transferringCalculation: transferringCalculation,
      getLinkFloatingAdjacents: getLinkFloatingAdjacents,
      getLinkAdjacents: getLinkAdjacents,
      close: close,
      open: open,
      openFloating: openFloating,
      openUnknown: openUnknown,
      isDirty: isDirty,
      setDirty: setDirty,
      saveTransfer: saveTransfer,
      cancel: cancel,
      cancelAfterDefloat: cancelAfterDefloat,
      cancelAndReselect: cancelAndReselect,
      clearAndReset: clearAndReset,
      continueSelectUnknown: continueSelectUnknown,
      isSelectedById: isSelectedById,
      isSelectedByLinkId: isSelectedByLinkId,
      setTrafficDirection: setTrafficDirection,
      setFunctionalClass: setFunctionalClass,
      setLinkType: setLinkType,
      setFloatingRoadMarker: setFloatingRoadMarker,
      getFloatingRoadMarker: getFloatingRoadMarker,
      getAnomalousMarkers: getAnomalousMarkers,
      setAnomalousMarkers: setAnomalousMarkers,
      get: get,
      count: count,
      openMultiple: openMultiple,
      featureExistsInSelection: featureExistsInSelection,
      isFloatingHomogeneous: isFloatingHomogeneous,
      getCurrentFloatings: getCurrentFloatings,
      getFeaturesToKeepFloatings: getFeaturesToKeepFloatings,
      getFeaturesToKeepUnknown: getFeaturesToKeepUnknown,
      isLinkIdInCurrent: isLinkIdInCurrent,
      isLinkIdInFeaturesToKeep: isLinkIdInFeaturesToKeep,
      filterFeaturesAfterSimulation: filterFeaturesAfterSimulation,
      linkIdsToExclude: linkIdsToExclude,
      extractDataForDisplay: extractDataForDisplay,
      setCurrent: setCurrent
    };
  };
})(this);
