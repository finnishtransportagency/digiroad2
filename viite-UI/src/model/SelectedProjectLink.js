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
    var isSelected = function(linkId) {
      return _.contains(ids, linkId);
    };

    return {
      open: open,
      get: get,
      isSelected: isSelected
    };
  };
})(this);
