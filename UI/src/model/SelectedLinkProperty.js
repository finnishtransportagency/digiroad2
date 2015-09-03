(function(root) {
  root.SelectedLinkProperty = function(backend, collection) {
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
      var extractMunicipalityCodes = function(selectedData) {
        return _.chain(selectedData)
          .pluck('municipalityCode')
          .uniq()
          .value();
      };
      var extractProperties = function(selection) {
        var properties = _.first(selection);
        var isMultiSelect = selection.length > 1;
        if (isMultiSelect) {
          var ambiguousFields = ['maxAddressNumberLeft', 'maxAddressNumberRight', 'minAddressNumberLeft', 'minAddressNumberRight'];
          properties = _.omit(properties, ambiguousFields);
        }
        return properties;
      };

      var latestModified = dateutil.extractLatestModifications(selectedData, 'modifiedAt');
      var municipalityCodes = {municipalityCode: extractMunicipalityCodes(selectedData)};
      return _.merge({}, extractProperties(selectedData), latestModified, municipalityCodes);
    };

    var open = function(id, singleLinkSelect) {
      if (!isSelected(id) || isDifferingSelection(singleLinkSelect)) {
        close();
        current = singleLinkSelect ? [collection.get(id)] : collection.getGroup(id);
        _.forEach(current, function (selected) {
          selected.select();
        });
        eventbus.trigger('linkProperties:selected', extractDataForDisplay(get()));
      }
    };

    var isDirty = function() {
      return dirty;
    };

    var isSelected = function(mmlId) {
      return _.some(current, function(selected) { return selected.getId() === mmlId; });
    };

    var save = function() {
      eventbus.trigger('linkProperties:saving');
      var mmlIds = _.map(current, function(selected) { return selected.getId(); });
      var modifications = _.first(current).getData();

      backend.updateLinkProperties(mmlIds, modifications, function() {
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
      eventbus.trigger('linkProperties:cancelled', originalData);
    };

    var setLinkProperty = function(key, value) {
      dirty = true;
      _.each(current, function(selected) { selected.setLinkProperty(key, value); });
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
      count: count
    };
  };
})(this);
