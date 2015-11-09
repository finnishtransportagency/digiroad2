(function(root) {
  root.SelectedPointAsset = function() {
    var self = this;
    var current = null;
    return {
      open: open,
      getId: getId
    };

    function open(asset) {
      current = asset;
      eventbus.trigger('pedestrianCrossing:selected');
    }

    function getId() {
      return current.id;
    }
  };
})(this);