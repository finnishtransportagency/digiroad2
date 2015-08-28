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

    var open = function(id) {
      if (!isSelected(id)) {
        close();
        current = collection.getGroup(id);
        _.forEach(current, function(selected) { selected.select(); });
        eventbus.trigger('linkProperties:selected', _.first(current).getData());
      }
    };

    var isDirty = function() {
      return dirty;
    };

    var isSelected = function(mmlId) {
      return _.some(current, function(selected) { return selected.getId() === mmlId; });
    };

    var save = function() {
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

    var setTrafficDirection = function(value) {
      dirty = true;
      _.each(current, function(selected) { selected.setTrafficDirection(value); });
    };
    var setFunctionalClass = function(value) {
      dirty = true;
      _.each(current, function(selected) { selected.setFunctionalClass(value); });
    };
    var setLinkType = function(value) {
      dirty = true;
      _.each(current, function(selected) { selected.setLinkType(value); });
    };

    var get = function() {
      return _.map(current, function(roadLink) {
        return roadLink.getData();
      });
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
      get: get
    };
  };
})(this);
