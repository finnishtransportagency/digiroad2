(function(root) {
  root.SelectedProjectLink = function(projectLinkCollection) {

    var current = [];
    var ids = [];
    var dirty = false;

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

    this.splitSuravageLink = function(id, split) {
      splitProjectLinks(id, split, function(splitSpeedLimits) {
        selection = [splitSpeedLimits.created, splitSpeedLimits.existing];
        originalSpeedLimitValue = splitSpeedLimits.existing.value;
        dirty = true;
        collection.setSelection(self);
        eventbus.trigger('speedLimit:selected', self);
      });
    };

    var splitSuravageLinks = function(id, split, callback) {
      // var link = _.find(_.flatten(speedLimits), { id: id });


      var left = _.cloneDeep(link);
      left.points = split.firstSplitVertices;

      var right = _.cloneDeep(link);
      right.points = split.secondSplitVertices;

      if (calculateMeasure(left) < calculateMeasure(right)) {
        splitSpeedLimits.created = left;
        splitSpeedLimits.existing = right;
      } else {
        splitSpeedLimits.created = right;
        splitSpeedLimits.existing = left;
      }

      splitSpeedLimits.created.id = null;
      splitSpeedLimits.splitMeasure = split.splitMeasure;

      splitSpeedLimits.created.marker = 'A';
      splitSpeedLimits.existing.marker = 'B';

      dirty = true;
      callback(splitSpeedLimits);
      eventbus.trigger('speedLimits:fetched', self.getAll());
    };

    var calculateMeasure = function(link) {
      var points = _.map(link.points, function(point) {
        return [point.x, point.y];
      });
      return new ol.geom.LineString(points).getLength();
    };

    this.isDirty = function() {
      return dirty;
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
