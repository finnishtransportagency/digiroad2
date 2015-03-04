(function(root) {
  root.SelectedManoeuvreSource = function(collection) {
    var current = null;

    var close = function() {
      if (current) {
        current = null;
        eventbus.trigger('manoeuvres:unselected');
      }
    };

    var open = function(roadLinkId) {
      if (!current || current.roadLinkId !== roadLinkId) {
        close();
        collection.get(roadLinkId, function(roadLink){
          current = roadLink;
          eventbus.trigger('manoeuvres:selected', roadLink);
        });
      }
    };

    var getRoadLinkId = function() {
      return current.roadLinkId;
    };

    var exists = function() {
      return current !== null;
    };

    var addManoeuvre = function(manoeuvre) {
      var newManoeuvre = _.merge({}, {sourceRoadLinkId: current.roadLinkId, sourceMmlID:current.mmlId}, manoeuvre);
      collection.addManoeuvre(newManoeuvre);
    };

    var removeManoeuvre = function(manoeuvre) {
      var manoeuvreToBeRemoved = _.merge({}, {sourceRoadLinkId: current.roadLinkId, sourceMmlID:current.mmlId}, manoeuvre);
      collection.removeManoeuvre(manoeuvreToBeRemoved);
    };

    var save = function() {
      collection.save(function() {
        eventbus.trigger('manoeuvres:saved', current);
      });
    };

    var cancel = function() {
      collection.cancelModifications();
      eventbus.trigger('manoeuvres:cancelled', current);
    };

    var isDirty = function() {
      return collection.isDirty();
    };

    return {
      close: close,
      open: open,
      getRoadLinkId: getRoadLinkId,
      exists: exists,
      addManoeuvre: addManoeuvre,
      removeManoeuvre: removeManoeuvre,
      save: save,
      cancel: cancel,
      isDirty: isDirty
    };
  };
})(this);
