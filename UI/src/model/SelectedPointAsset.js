(function(root) {
  root.SelectedPointAsset = function() {
    var self = this;
    var current = null;
    return {
      open: open,
      getId: getId
    };

    function open(id) {
      current = id;
      eventbus.trigger('pedestrianCrossing:selected');
    }

    function getId() {
      return current;
    }
  };
})(this);