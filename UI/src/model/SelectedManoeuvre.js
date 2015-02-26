(function(root) {
  root.SelectedManoeuvre = function(collection) {
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

    var add = function(destRoadLinkId) {
      console.log('adding manoeuvre to link: ', destRoadLinkId);
    };

    var remove = function(destRoadLinkId) {
      console.log('removing manoeuvre from link: ', destRoadLinkId);
    };

    return {
      close: close,
      open: open,
      getRoadLinkId: getRoadLinkId,
      exists: exists,
      add: add,
      remove: remove
    };
  };
})(this);
