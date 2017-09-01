(function(root) {
  root.SelectedProjectLink = function(projectLinkCollection) {

    var current = [];
    var ids = [];

    var open = function (linkid, multiSelect) {
      if (!multiSelect) {
        current = projectLinkCollection.getByLinkId([linkid]);
        ids = [linkid];
      } else {
        ids = projectLinkCollection.getMultiSelectIds(linkid);
        current = projectLinkCollection.getByLinkId(ids);
      }
      eventbus.trigger('projectLink:clicked', get());
    };
    var get = function() {
      return _.map(current, function(projectLink) {
        return projectLink.getData();
      });
    };
    var setCurrent = function(newSelection) {
      current = newSelection;
    };
    var isSelected = function(linkId) {
      return _.contains(ids, linkId);
    };

    var clean = function(){
      current = [];
      projectLinkCollection.setDirty(current);
    };

    var close = function(){
      current = [];
      projectLinkCollection.setDirty(current);
      eventbus.trigger('layer:enableButtons', true);
    };

    return {
      open: open,
      get: get,
      clean: clean,
      close: close,
      isSelected: isSelected,
      setCurrent: setCurrent
    };
  };
})(this);
