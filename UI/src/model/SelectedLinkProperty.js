(function(root) {
  root.SelectedLinkProperty = function(backend, collection) {

    var close = function() {
      eventbus.trigger('linkProperties:unselected');
    };

    var open = function(id) {
      close();
      var roadLink = collection.get(id);
      eventbus.trigger('linkProperties:selected', roadLink);
    };

    return {
      close: close,
      open: open
    };
  };
})(this);