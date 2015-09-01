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

    var open = function(id, singleLinkSelect) {
      if (!isSelected(id) || isDifferingSelection(singleLinkSelect)) {
        close();
        current = singleLinkSelect ? [collection.get(id)] : collection.getGroup(id);
        _.forEach(current, function(selected) { selected.select(); });
        var data =  _.first(current).getData();
        data.modifiedBy = getModifiedBy();
        data.modifiedAt = getModifiedDateTime();
        eventbus.trigger('linkProperties:selected', data);
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

      backend.updateLinkProperties(mmlIds, modifications, function(linkProperties) {
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

    var segmentWithLatestModifications = function() {
      return _.last(_.sortBy(get(), function(s) {
        return moment(s.modifiedAt, "DD.MM.YYYY HH:mm:ss").valueOf() || 0;
      }));
    };

    var getModifiedBy = function() {
      return segmentWithLatestModifications().modifiedBy;
    };

    var getModifiedDateTime = function() {
      return segmentWithLatestModifications().modifiedAt;
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
