(function(root) {
  root.SelectedLinkProperty = function(backend, roadCollection) {
    var current = [];
    var dirty = false;

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
      var pickUniqueValues = function(selectedData, property) {
        return _.chain(selectedData)
          .map(property)
          .uniq()
          .value();
      };

      var extractUniqueValues = function(selectedData, property) {
        return pickUniqueValues(selectedData, property).join(', ');
      };

      var extractMinAddressValue = function(selectedData, property) {
        var roadPartNumber = Math.min.apply(null, _.compact(pickUniqueValues(selectedData, 'roadPartNumber')));
        return Math.min.apply(null, _.chain(selectedData)
          .filter(function (data) {
            return data.roadPartNumber == roadPartNumber;
          })
          .map(property)
          .value());
      };

      var extractMaxAddressValue = function(selectedData, property) {
        var roadPartNumber = Math.max.apply(null, _.compact(pickUniqueValues(selectedData, 'roadPartNumber')));
        return Math.max.apply(null, _.chain(selectedData)
          .filter(function (data) {
            return data.roadPartNumber == roadPartNumber;
          })
          .map(property)
          .value());
      };

      var properties = _.cloneDeep(_.head(selectedData));
      var isMultiSelect = selectedData.length > 1;
      if (isMultiSelect) {
        var ambiguousFields = ['municipalityCode', 'surfaceRelation', 'roadNameFi', 'roadNameSe', 'modifiedAt', 'modifiedBy'];
        properties = _.omit(properties, ambiguousFields);
        var latestModified = dateutil.extractLatestModifications(selectedData);
        var municipalityCodes = {municipalityCode: extractUniqueValues(selectedData, 'municipalityCode')};
        var surfaceRelations = {surfaceRelation: extractUniqueValues(selectedData, 'surfaceRelation')};
        var roadNumbers = extractUniqueValues(selectedData, 'roadNumber');
        var roadPartNumbers = {roadPartNumber: null};
        var startAddrMValue = {startAddrMValue: null};
        var endAddrMValue = {endAddrMValue: null};
        // Don't show address data if multiple roads (with distinct road numbers) are selected
        if (roadNumbers !== null && roadNumbers.length > 0 && !roadNumbers.match("/,/")) {
          roadPartNumbers = {roadPartNumber: extractUniqueValues(selectedData, 'roadPartNumber')};
          startAddrMValue = {startAddrMValue: extractMinAddressValue(selectedData, 'startAddrMValue')};
          endAddrMValue = {endAddrMValue: extractMaxAddressValue(selectedData, 'endAddrMValue')};
        }

        var roadNames = {
          roadNameFi: extractUniqueValues(selectedData, 'roadNameFi'),
          roadNameSe: extractUniqueValues(selectedData, 'roadNameSe')
        };
        _.merge(properties, latestModified, municipalityCodes, surfaceRelations, roadPartNumbers, roadNames, startAddrMValue, endAddrMValue);
      }

      return properties;
    };

    var open = function(id, singleLinkSelect) {
      if (!isSelected(id) || isDifferingSelection(singleLinkSelect)) {
        close();
        current = singleLinkSelect ? roadCollection.get([id]) : roadCollection.getGroup(id);
        _.forEach(current, function (selected) {
          selected.select();
        });
        eventbus.trigger('linkProperties:selected', extractDataForDisplay(get()));
      }
    };

    var openMultiple = function(links) {
      var uniqueLinks = _.uniq(links, 'linkId');
      current = roadCollection.get(_.map(uniqueLinks, 'linkId'));
      _.forEach(current, function (selected) {
        selected.select();
      });
      eventbus.trigger('linkProperties:multiSelected', extractDataForDisplay(get()));
    };

    var isDirty = function() {
      return dirty;
    };

    var isSelected = function(linkId) {
      return _.some(current, function(selected) { return selected.getId() === linkId; });
    };

    var save = function() {
      eventbus.trigger('linkProperties:saving');
      var linkIds = _.map(current, function(selected) { return selected.getId(); });
      var modifications = _.map(current, function(c) { return c.getData(); });

      var roadAssociationNames = _.map(modifications, function(modification){ return modification.privateRoadAssociation; });

      backend.updateLinkProperties(linkIds, modifications, function() {
        dirty = false;
        eventbus.trigger('linkProperties:saved');

        if(roadAssociationNames.length > 0)
          eventbus.trigger('associationNames:reload');

      }, function() {
        eventbus.trigger('linkProperties:updateFailed');
      });
    };

    var cancel = function() {
      dirty = false;
      _.each(current, function(selected) { selected.cancel(); });
      var originalData = _.head(current).getData();
      eventbus.trigger('linkProperties:cancelled', _.cloneDeep(originalData));
    };

    var cancelDirectionChange = function() {
      _.each(current, function(selected) { selected.cancelDirectionChange(); });
      var originalData = _.head(current).getData();
      eventbus.trigger('linkProperties:cancelledDirectionChange', _.cloneDeep(originalData));
    };

    var setLinkProperty = function(key, value) {
      dirty = true;
      _.each(current, function(selected) { selected.setLinkProperty(key, value); });
      eventbus.trigger('linkProperties:changed');
    };
    var setTrafficDirection = _.partial(setLinkProperty, 'trafficDirection');
    var setFunctionalClass = _.partial(setLinkProperty, 'functionalClass');
    var setLinkType = _.partial(setLinkProperty, 'linkType');
    var setAdministrativeClass = _.partial(setLinkProperty, 'administrativeClass');
    var setAccessRightId =  _.partial(setLinkProperty, 'accessRightID');
    var setPrivateRoadAssociation =  _.partial(setLinkProperty, 'privateRoadAssociation');
    var setAdditionalInfo =  _.partial(setLinkProperty, 'additionalInfo');

    var get = function() {
      return _.map(current, function(roadLink) {
        return roadLink.getData();
      });
    };

    var count = function() {
      return current.length;
    };

    return {
      close: close,
      open: open,
      isDirty: isDirty,
      save: save,
      cancel: cancel,
      cancelDirectionChange: cancelDirectionChange,
      isSelected: isSelected,
      setTrafficDirection: setTrafficDirection,
      setFunctionalClass: setFunctionalClass,
      setLinkType: setLinkType,
      setAdministrativeClass: setAdministrativeClass,
      setAccessRightId : setAccessRightId,
      setPrivateRoadAssociation: setPrivateRoadAssociation,
      setAdditionalInfo: setAdditionalInfo,
      get: get,
      count: count,
      openMultiple: openMultiple
    };
  };
})(this);
