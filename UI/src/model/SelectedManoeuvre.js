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
        eventbus.trigger('manoeuvres:selected', collection.get(roadLinkId));
      }
    };

    var getRoadLinkId = function() {
      return current;
    };

    return {
      close: close,
      open: open,
      getRoadLinkId: getRoadLinkId
    };
  };
})(this);
