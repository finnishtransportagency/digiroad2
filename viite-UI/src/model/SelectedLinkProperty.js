(function(root) {
  root.SelectedLinkProperty = function(backend, roadCollection) {
    var current = [];
    var dirty = false;
    var targets = [];

    var markers = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
      "AA", "AB", "AC", "AD", "AE", "AF", "AG", "AH", "AI", "AJ", "AK", "AL", "AM", "AN", "AO", "AP", "AQ", "AR", "AS", "AT", "AU", "AV", "AW", "AX", "AY", "AZ",
      "BA", "BB", "BC", "BD", "BE", "BF", "BG", "BH", "BI", "BJ", "BK", "BL", "BM", "BN", "BO", "BP", "BQ", "BR", "BS", "BT", "BU", "BV", "BW", "BX", "BY", "BZ",
      "CA", "CB", "CC", "CD", "CE", "CF", "CG", "CH", "CI", "CJ", "CK", "CL", "CM", "CN", "CO", "CP", "CQ", "CR", "CS", "CT", "CU", "CV", "CW", "CX", "CY", "CZ"];

    var close = function() {
      if (!_.isEmpty(current) && !isDirty()) {
        _.forEach(current, function(selected) { selected.unselect(); });
        eventbus.trigger('linkProperties:unselected');
        current = [];
        dirty = false;
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

        var roadNames = {
          roadNameFi: extractUniqueValues(selectedData, 'roadNameFi'),
          roadNameSe: extractUniqueValues(selectedData, 'roadNameSe'),
          roadNameSm: extractUniqueValues(selectedData, 'roadNameSm')
        };
        _.merge(properties, latestModified, municipalityCodes, verticalLevels, roadPartNumbers, roadNames, elyCodes, startAddressM, endAddressM);
      }
      return properties;
    };

    var open = function(linkId, id, singleLinkSelect, visibleFeatures) {
      var canIOpen = !_.isUndefined(linkId) ? !isSelectedByLinkId(linkId) || isDifferingSelection(singleLinkSelect) : !isSelectedById(id) || isDifferingSelection(singleLinkSelect);
      if (canIOpen) {
        close();
        if(!_.isUndefined(linkId)){
          current = singleLinkSelect ? roadCollection.getByLinkId([linkId]) : roadCollection.getGroupByLinkId(linkId);  
        } else {
          current = singleLinkSelect ? roadCollection.getById([id]) : roadCollection.getGroupById(id);
        }
        
        _.forEach(current, function (selected) {
          selected.select();
        });

        var selectedOL3Features = _.filter(visibleFeatures, function(vf){
          return (_.some(get(), function(s){
              return s.linkId === vf.roadLinkData.linkId;
            })) && (_.some(get(), function(s){
              return s.mmlId === vf.roadLinkData.mmlId;
            }));
        });
        eventbus.trigger('linkProperties:ol3Selected', selectedOL3Features);
        eventbus.trigger('linkProperties:selected', extractDataForDisplay(get()));
      }
    };

    var getLinkAdjacents = function(link) {
      var linkIds = {};
       backend.getFloatingAdjacent(link.linkId, link.roadNumber, link.roadPartNumber, link.trackCode, function(adjacents) {
        if(!_.isEmpty(adjacents))
          linkIds = adjacents;
         if(!applicationModel.isReadOnly()){
           var calculatedRoads = {"adjacents" : _.map(adjacents, function(a, index){
             return _.merge({}, a, {"marker": markers[index]});
         }), "links": link};
           eventbus.trigger("adjacents:added",calculatedRoads.links, calculatedRoads.adjacents );
         }
       });
      return linkIds;
    };

    eventbus.on("adjacents:additionalSourceSelected", function(existingSources, additionalSourceLinkId) {

      var newSources = [existingSources].concat([roadCollection.getRoadLinkByLinkId(parseInt(additionalSourceLinkId)).getData()]);
      var data = _.map(newSources, function (ns){
        return {"linkId": ns.linkId, "roadNumber": ns.roadNumber, "roadPartNumber": ns.roadPartNumber, "trackCode": ns.trackCode};
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

    var addTargets = function(target, adjacents){
      if(!_.contains(targets,target))
        targets.push(target);
      var targetData = _.filter(adjacents, function(adjacent){
        return adjacent.linkId == target;
      });
      //TODO bellow trigger refresh next target adjacents in the form
      if(!_.isEmpty(targetData)){
        
        $('#aditionalSource').remove();
        $('#adjacentsData').remove();
        getLinkAdjacents(_.first(targetData));
      }
    };

    var getTargets = function(){
      return _.union(targets);
    };

    var cancel = function() {
      dirty = false;
      _.each(current, function(selected) { selected.cancel(); });
      if(!_.isUndefined(_.first(current))){
        var originalData = _.first(current).getData();
        eventbus.trigger('linkProperties:cancelled', _.cloneDeep(originalData));
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

    var count = function() {
      return current.length;
    };

    return {
      addTargets: addTargets,
      getTargets: getTargets,
      getLinkAdjacents: getLinkAdjacents,
      close: close,
      open: open,
      isDirty: isDirty,
      save: save,
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
