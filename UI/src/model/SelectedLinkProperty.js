(function(root) {
  root.SelectedLinkProperty = function(backend, collection) {
    var current = [];

    var close = function() {
      if (!_.isEmpty(current) && !isDirty()) {
        _.forEach(current, function(selected) { selected.unselect(); });
        eventbus.trigger('linkProperties:unselected');
        current = [];
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
      return _.some(current, function(selected) { return selected.isDirty(); });
    };

    var isSelected = function(mmlId) {
      return _.some(current, function(selected) { return selected.getId() === mmlId; });
    };

    var save = function() {
      current.save(backend);
    };

    var cancel = function() {
      _.each(current, function(selected) { selected.cancel(); });
    };

    var setTrafficDirection = function(value) {
      _.each(current, function(selected) { selected.setTrafficDirection(value); });
    };
    var setFunctionalClass = function(value) {
      _.each(current, function(selected) { selected.setFunctionalClass(value); });
    };
    var setLinkType = function(value) {
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
