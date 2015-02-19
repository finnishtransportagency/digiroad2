(function(root) {
  root.SelectedManoeuvre = function() {
    var current = null;

    var close = function() {
      if (current) {
        current = null;
      }
    };

    var open = function(roadLinkId) {
      if (current !== roadLinkId) {
        close();
        current = roadLinkId;
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
