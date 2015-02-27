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
      if (current !== roadLinkId) {
        close();
        current = roadLinkId;
        collection.get(roadLinkId, function(roadLink){
          eventbus.trigger('manoeuvres:selected', roadLink);
        });
      }
    };

    var getRoadLinkId = function() {
      return current;
    };

    var exists = function() {
      return current !== null;
    };

    var addManoeuvreTo = function(destRoadLinkId) {
      eventbus.trigger('manoeuvre:changed');
    };

    var removeManoeuvreTo = function(destRoadLinkId) {
      eventbus.trigger('manoeuvre:changed');
    };

    return {
      close: close,
      open: open,
      getRoadLinkId: getRoadLinkId,
      exists: exists,
      addManoeuvreTo: addManoeuvreTo,
      removeManoeuvreTo: removeManoeuvreTo
    };
  };
})(this);
