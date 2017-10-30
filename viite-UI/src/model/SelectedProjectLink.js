(function(root) {
  root.SelectedProjectLink = function(projectLinkCollection) {

    var current = [];
    var ids = [];
    var dirty = false;
    var splitSuravage = {};
    var LinkGeomSource = LinkValues.LinkGeomSource;

    var open = function (linkid, multiSelect) {
      if (!multiSelect) {
        current = projectLinkCollection.getByLinkId([linkid]);
        ids = [linkid];
      } else {
        ids = projectLinkCollection.getMultiSelectIds(linkid);
        current = projectLinkCollection.getByLinkId(_.flatten(ids));
      }
      eventbus.trigger('projectLink:clicked', get());
    };

    var openSplit = function (linkid, multiSelect) {
      if (!multiSelect) {
        current = projectLinkCollection.getByLinkId([linkid]);
        ids = [linkid];
      } else {
        ids = projectLinkCollection.getMultiSelectIds(linkid);
        current = projectLinkCollection.getByLinkId(_.flatten(ids));
      }
      var splitLinks =  _.partition(get(), function(link){
        return link.roadLinkSource === LinkGeomSource.SuravageLinkInterface.value && !_.isUndefined(link.connectedLinkId);
      });
      var orderedSplitParts = _.sortBy(splitLinks[0], [
        function (s) {
          return (_.isUndefined(s.points) || _.isUndefined(s.points[0])) ? Infinity : s.points[0].y;},
        function (s) {
          return (_.isUndefined(s.points) || _.isUndefined(s.points[0])) ? Infinity : s.points[0].x;}]);
      var suravageA = orderedSplitParts[0];
      var suravageB = orderedSplitParts[1];
       suravageA.marker = "A";
       suravageB.marker = "B";
      eventbus.trigger('split:projectLinks', [suravageA, suravageB]);
    };

    var splitSuravageLink = function(suravage, split, mousePoint) {
      splitSuravageLinks(suravage, split, mousePoint, function(splitSuravageLinks) {
        var selection = [splitSuravageLinks.created, splitSuravageLinks.existing];
        eventbus.trigger('split:projectLinks', selection);
      });
    };

    var splitSuravageLinks = function(nearestSuravage, split, mousePoint, callback) {
      var left = _.cloneDeep(nearestSuravage);
      left.points = split.firstSplitVertices;

      var right = _.cloneDeep(nearestSuravage);
      right.points = split.secondSplitVertices;
      var measureLeft = calculateMeasure(left);
      var measureRight = calculateMeasure(right);
      splitSuravage.created = left;
      splitSuravage.created.endMValue = measureLeft;
      splitSuravage.existing = right;
      splitSuravage.existing.endMValue = measureRight;
      splitSuravage.created.splitPoint = mousePoint;
      splitSuravage.existing.splitPoint = mousePoint;

      splitSuravage.created.id = null;
      splitSuravage.splitMeasure = split.splitMeasure;

      splitSuravage.created.marker = 'A';
      splitSuravage.existing.marker = 'B';

      callback(splitSuravage);
    };

    var calculateMeasure = function(link) {
      var points = _.map(link.points, function(point) {
        return [point.x, point.y];
      });
      return new ol.geom.LineString(points).getLength();
    };

    var isDirty = function() {
      return dirty;
    };

    var setDirty = function(value) {
      dirty = value;
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
      return _.contains(_.flatten(ids), linkId);
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
      openSplit: openSplit,
      get: get,
      clean: clean,
      cleanIds: cleanIds,
      close: close,
      isSelected: isSelected,
      setCurrent: setCurrent,
      isDirty: isDirty,
      setDirty: setDirty,
      splitSuravageLink: splitSuravageLink
    };
  };
})(this);
