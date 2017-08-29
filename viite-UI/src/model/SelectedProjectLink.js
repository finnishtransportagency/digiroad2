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

    var openShift = function(linkIds) {
      if (linkIds.length === 0) {
        cleanIds();
        close();
      } else {
        var added = _.difference(linkIds, ids);
        ids = linkIds;
        current = _.filter(current, function(link) {
          return _.contains(linkIds, link.getData().linkId);
          }
        );
        current = current.concat(projectLinkCollection.getByLinkId(added));
        eventbus.trigger('projectLink:clicked', get());
      }
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
    };

    var cleanIds = function(){
      ids = [];
    };

    var close = function(){
      current = [];
      eventbus.trigger('layer:enableButtons', true);
    };

    return {
      open: open,
      openShift: openShift,
      get: get,
      clean: clean,
      cleanIds: cleanIds,
      close: close,
      isSelected: isSelected,
      setCurrent: setCurrent
    };
  };
})(this);
