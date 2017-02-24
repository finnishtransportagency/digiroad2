(function(root) {
  root.SelectedLinkProperty = function(backend, roadCollection) {
    var current = [];
    var dirty = false;
    var targets = [];
    var sources = [];
    var featuresToKeep = [];

    var markers = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
      "AA", "AB", "AC", "AD", "AE", "AF", "AG", "AH", "AI", "AJ", "AK", "AL", "AM", "AN", "AO", "AP", "AQ", "AR", "AS", "AT", "AU", "AV", "AW", "AX", "AY", "AZ",
      "BA", "BB", "BC", "BD", "BE", "BF", "BG", "BH", "BI", "BJ", "BK", "BL", "BM", "BN", "BO", "BP", "BQ", "BR", "BS", "BT", "BU", "BV", "BW", "BX", "BY", "BZ",
      "CA", "CB", "CC", "CD", "CE", "CF", "CG", "CH", "CI", "CJ", "CK", "CL", "CM", "CN", "CO", "CP", "CQ", "CR", "CS", "CT", "CU", "CV", "CW", "CX", "CY", "CZ"];

    var close = function() {
      if (!_.isEmpty(current) && !isDirty()) {
        _.forEach(current, function(selected) { selected.unselect(); });
        eventbus.trigger('linkProperties:unselected');
        current = [];
        sources = [];
        targets = [];
        dirty = false;
        featuresToKeep = [];
      }
    };

    var isSingleLinkSelection = function() {
      return current.length === 1;
    };

    var isDifferingSelection = function(singleLinkSelect) {
      return (!_.isUndefined(singleLinkSelect) &&
              (singleLinkSelect !== isSingleLinkSelection()));
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

    var open = function(linkId, id, singleLinkSelect) {
      var canIOpen = !_.isUndefined(linkId) ? !isSelectedByLinkId(linkId) || isDifferingSelection(singleLinkSelect) : !isSelectedById(id) || isDifferingSelection(singleLinkSelect);
      if (canIOpen) {
        if(featuresToKeep.length === 0){
          close();
        } else {
          if (!_.isEmpty(current) && !isDirty()) {
            _.forEach(current, function(selected) { selected.unselect(); });
          }
        }
        if(!_.isUndefined(linkId)){
          current = singleLinkSelect ? roadCollection.getByLinkId([linkId]) : roadCollection.getGroupByLinkId(linkId);  
        } else {
          current = singleLinkSelect ? roadCollection.getById([id]) : roadCollection.getGroupById(id);
        }
        
        _.forEach(current, function (selected) {
          selected.select();
        });
        var data4Display = extractDataForDisplay(get());
        if(!applicationModel.isReadOnly() && get()[0].roadLinkType === -1){
          if (!_.isEmpty(featuresToKeep)) {
            applicationModel.addSpinner();
          }
          featuresToKeep.push(data4Display);
        }
        var contains = _.find(featuresToKeep, function(fk){
          return fk.linkId === data4Display.linkId;
        });
        if(!_.isEmpty(featuresToKeep) && _.isUndefined(contains)){
          featuresToKeep.push(data4Display);
        }
        eventbus.trigger('linkProperties:selected', data4Display);
      }
    };

    var getLinkAdjacents = function(link) {
      var linkIds = {};
      var chainLinks = [];
      _.each(current, function (link) {
        if (!_.isUndefined(link))
          chainLinks.push(link.getData().linkId);
      });
      _.each(targets, function (link) {
        chainLinks.push(link.getData().linkId);
      });
      var data = {
        "selectedLinks": _.uniq(chainLinks), "linkId": parseInt(link.linkId), "roadNumber": parseInt(link.roadNumber),
        "roadPartNumber": parseInt(link.roadPartNumber), "trackCode": parseInt(link.trackCode)
      };

      if (featuresToKeep.length > 1){
        applicationModel.addSpinner();
        backend.getFloatingAdjacent(data, function (adjacents) {
          applicationModel.removeSpinner();
          if (!_.isEmpty(adjacents))
            linkIds = adjacents;
          if (!applicationModel.isReadOnly()) {
            var selectedLinkIds = _.map(get().concat(featuresToKeep), function (roads) {
              return roads.linkId;
            });
            var filteredAdjacents = _.filter(adjacents, function(adj){
              return !_.contains(selectedLinkIds, adj.linkId);
            });
            var calculatedRoads = {
              "adjacents": _.map(filteredAdjacents, function (a, index) {
                return _.merge({}, a, {"marker": markers[index]});
              }), "links": link
            };
            eventbus.trigger("adjacents:added", calculatedRoads.links, calculatedRoads.adjacents);
            eventbus.trigger('adjacents:startedFloatingTransfer');
          }
        });
      }
      return linkIds;
    };

    eventbus.on("adjacents:additionalSourceSelected", function(existingSources, additionalSourceLinkId) {
      sources = current;
      sources.push(roadCollection.getRoadLinkByLinkId(parseInt(additionalSourceLinkId)));
      var chainLinks = [];
      _.each(sources, function(link){
        if(!_.isUndefined(link))
        chainLinks.push(link.getData().linkId);
      });
      _.each(targets, function(link){
        chainLinks.push(link.getData().linkId);
      });
      var newSources = [existingSources];
      if(!_.isUndefined(additionalSourceLinkId))
        newSources.push(roadCollection.getRoadLinkByLinkId(parseInt(additionalSourceLinkId)).getData());
      var data = _.map(newSources, function (ns){
        return {"selectedLinks": _.uniq(chainLinks), "linkId": parseInt(ns.linkId), "roadNumber": parseInt(ns.roadNumber),
          "roadPartNumber": parseInt(ns.roadPartNumber), "trackCode": parseInt(ns.trackCode)};
      });
     backend.getAdjacentsFromMultipleSources(data, function(adjacents){
       if(!_.isEmpty(adjacents) && !applicationModel.isReadOnly()){
         var calculatedRoads = {"adjacents" : _.map(adjacents, function(a, index){
           return _.merge({}, a, {"marker": markers[index]});
         }), "links": newSources};
         eventbus.trigger("adjacents:aditionalSourceFound",calculatedRoads.links, calculatedRoads.adjacents, additionalSourceLinkId);
         eventbus.trigger('adjacents:startedFloatingTransfer');
       } else {
        applicationModel.removeSpinner();
       }
      });
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

    var save = function() {
      eventbus.trigger('linkProperties:saving');
      var linkIds = _.map(current, function(selected) { return selected.getId(); });
      var modifications = _.map(current, function(c) { return c.getData(); });

      backend.updateLinkProperties(linkIds, modifications, function() {
        dirty = false;
        eventbus.trigger('linkProperties:saved');
      }, function() {
        eventbus.trigger('linkProperties:updateFailed');
      });
    };

    var transferringCalculation = function(){
      var targetsData = _.map(targets,function (t){
        return t.getData();
      });
      var targetDataIds = _.uniq(_.filter(_.map(targetsData.concat(featuresToKeep), function(feature){
        if(feature.roadLinkType != -1 && feature.anomaly == 1){
          return feature.linkId.toString();
        }
      }), function (target){
        return !_.isUndefined(target);
      }));
      var sourceDataIds = _.filter(_.map(get().concat(featuresToKeep), function (feature) {
        if(feature.roadLinkType == -1){
          return feature.linkId.toString();
        }
      }), function (source){
        return !_.isUndefined(source);
      });
      var data = {"sourceLinkIds": _.uniq(sourceDataIds), "targetLinkIds":_.uniq(targetDataIds)};

      backend.getTransferResult(data, function(result) {
        if(!_.isEmpty(result) && !applicationModel.isReadOnly()) {
          eventbus.trigger("adjacents:roadTransfer", result, sourceDataIds.concat(targetDataIds), targetDataIds);
          roadCollection.setNewTmpRoadAddress(result);
        }
      });

    };

    var saveTransfer = function() {
      eventbus.trigger('linkProperties:saving');
      var roadAddresses = roadCollection.getNewTmpRoadAddress();

      var targetsData = _.map(targets,function (t){
        return t.getData();
      });

      var targetDataIds = _.uniq(_.filter(_.map(targetsData.concat(featuresToKeep), function(feature){
        if(feature.roadLinkType != -1 && feature.anomaly == 1){
          return feature.linkId;
        }
      }), function (target){
        return !_.isUndefined(target);
      }));
      var sourceDataIds = _.filter(_.map(get().concat(featuresToKeep), function (feature) {
        if(feature.roadLinkType == -1){
          return feature.linkId;
        }
      }), function (source){
        return !_.isUndefined(source);
      });

      var data = {'sourceIds': sourceDataIds, 'targetIds': targetDataIds, 'roadAddress': roadAddresses};

      backend.createRoadAddress(data, function() {
        dirty = false;
        eventbus.trigger('linkProperties:saved');
      }, function() {
        eventbus.trigger('linkProperties:updateFailed');
      });
      targets = [];
      applicationModel.setActiveButtons(false);
    };

    var addTargets = function(target, adjacents){
      targets.push(roadCollection.getRoadLinkByLinkId(parseInt(target)));
      var targetData = _.filter(adjacents, function(adjacent){
        return adjacent.linkId == target;
      });
      if(!_.isEmpty(targetData)){
        $('#aditionalSource').remove();
        $('#adjacentsData').remove();
        getLinkAdjacents(_.first(targetData));
      }
    };

    var getSources = function() {
      return _.union(_.map(sources, function (roadLink) {
        return roadLink.getData();
      }));
    };

    var getTargets = function(){
      return _.union(_.map(targets, function (roadLink) {
        return roadLink.getData();
      }));
    };

    var resetSources = function() {
      sources = [];
      return sources;
    };

    var resetTargets = function() {
      targets = [];
      return targets;
    };

    var cancel = function(action, changedTargetIds) {
      dirty = false;
      _.each(current, function(selected) { selected.cancel(); });
      var originalData = _.first(featuresToKeep);
      if(action !== applicationModel.actionCalculated && action !== applicationModel.actionCalculating)
        clearFeaturesToKeep();
      if(_.isEmpty(changedTargetIds)) {
        roadCollection.resetTmp();
        roadCollection.resetChangedIds();
        clearFeaturesToKeep();
        _.defer(function(){
          eventbus.trigger('linkProperties:selected', _.cloneDeep(originalData));
        });
      }

      $('#adjacentsData').remove();
      if(applicationModel.isActiveButtons() || action === -1){
        if(action !== applicationModel.actionCalculated){
          applicationModel.setActiveButtons(false);
          eventbus.trigger('roadLinks:deleteSelection');
        }
        eventbus.trigger('roadLinks:deleteSelection');
        eventbus.trigger('roadLinks:fetched', action, changedTargetIds);
      }
    };

    var gapTransferingCancel = function(){
      //First we grab the floatings
      var floatings = _.uniq(_.filter(_.map(featuresToKeep, function(feature){
        if(feature.roadLinkType === -1){
          return feature.linkId;
        }
      }), function (target){
        return !_.isUndefined(target);
      }));
      //Secondly we clear them
      clearFeaturesToKeep();
      applicationModel.setActiveButtons(false);
      eventbus.trigger('roadLinks:deleteSelection');

      if (!_.isEmpty(current) && !isDirty()) {
        _.forEach(current, function (selected) {
          selected.unselect();
        });
        eventbus.trigger('linkProperties:unselected');
        sources = [];
        targets = [];
        current = [];
        featuresToKeep = [];
      }
      _.forEach(floatings, function(f){
        var roadAddress = roadCollection.getByLinkId([f]);
        var roads = _.map(get(), function (r){
          return r.linkId;
        });
        var fetchedRoads = _.map(roadAddress, function (r){
          return r.getData().linkId;
        });
        if(!_.contains(roads, _.first(fetchedRoads))){
          current = current.concat(roadAddress);
        }
      });
      eventbus.trigger('roadLinks:drawAfterGapCanceling');
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

    var count = function() {
      return current.length;
    };

    eventbus.on("roadLink:editModeAdjacents", function(){
      if(!applicationModel.isReadOnly() && !applicationModel.isActiveButtons() && count() > 0) {
        eventbus.trigger("linkProperties:selected", extractDataForDisplay(get()));
      }
    });

    var getFeaturesToKeep = function(){
      return featuresToKeep;
    };

    var clearFeaturesToKeep = function() {
      featuresToKeep = [];
    };

    return {
      getSources: getSources,
      resetSources: resetSources,
      addTargets: addTargets,
      getTargets: getTargets,
      resetTargets: resetTargets,
      getFeaturesToKeep: getFeaturesToKeep,
      clearFeaturesToKeep: clearFeaturesToKeep,
      transferringCalculation: transferringCalculation,
      getLinkAdjacents: getLinkAdjacents,
      gapTransferingCancel: gapTransferingCancel,
      close: close,
      open: open,
      isDirty: isDirty,
      save: save,
      saveTransfer: saveTransfer,
      cancel: cancel,
      isSelectedById: isSelectedById,
      isSelectedByLinkId: isSelectedByLinkId,
      setTrafficDirection: setTrafficDirection,
      setFunctionalClass: setFunctionalClass,
      setLinkType: setLinkType,
      get: get,
      count: count,
      openMultiple: openMultiple
    };
  };
})(this);
