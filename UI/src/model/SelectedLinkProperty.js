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
          .pluck(property)
          .uniq()
          .value();
      };

      var extractUniqueValues = function(selectedData, property) {
        return pickUniqueValues(selectedData, property).join(', ');
      };

      var extractMinAddressValue = function(selectedData, property) {
        var roadPartNumber = Math.min.apply(null, pickUniqueValues(selectedData, 'roadPartNumber'));
        return Math.min.apply(null, _.chain(selectedData)
          .filter(function (data) {
            return data.roadPartNumber == roadPartNumber;
          })
          .pluck(property)
          .value());
      };

      var extractMaxAddressValue = function(selectedData, property) {
        var roadPartNumber = Math.max.apply(null, pickUniqueValues(selectedData, 'roadPartNumber'));
        return Math.max.apply(null, _.chain(selectedData)
          .filter(function (data) {
            return data.roadPartNumber == roadPartNumber;
          })
          .pluck(property)
          .value());
      };

      var properties = _.cloneDeep(_.first(selectedData));
      var isMultiSelect = selectedData.length > 1;
      if (isMultiSelect) {
        var ambiguousFields = ['maxAddressNumberLeft', 'maxAddressNumberRight', 'minAddressNumberLeft', 'minAddressNumberRight',
          'municipalityCode', 'verticalLevel', 'roadNameFi', 'roadNameSe', 'roadNameSm', 'modifiedAt', 'modifiedBy'];
        properties = _.omit(properties, ambiguousFields);
        var latestModified = dateutil.extractLatestModifications(selectedData);
        var municipalityCodes = {municipalityCode: extractUniqueValues(selectedData, 'municipalityCode')};
        var verticalLevels = {verticalLevel: extractUniqueValues(selectedData, 'verticalLevel')};
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
          roadNameSe: extractUniqueValues(selectedData, 'roadNameSe'),
          roadNameSm: extractUniqueValues(selectedData, 'roadNameSm')
        };
        _.merge(properties, latestModified, municipalityCodes, verticalLevels, roadPartNumbers, roadNames, startAddrMValue, endAddrMValue);
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

    var isSelected = function(linkId) {
      return _.some(current, function(selected) { return selected.getId() === linkId; });
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

    var cancel = function() {
      dirty = false;
      _.each(current, function(selected) { selected.cancel(); });
      var originalData = _.first(current).getData();
      eventbus.trigger('linkProperties:cancelled', _.cloneDeep(originalData));
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
      close: close,
      open: open,
      isDirty: isDirty,
      save: save,
      cancel: cancel,
      isSelected: isSelected,
      setTrafficDirection: setTrafficDirection,
      setFunctionalClass: setFunctionalClass,
      setLinkType: setLinkType,
      get: get,
      count: count,
      openMultiple: openMultiple
    };
  };
})(this);
