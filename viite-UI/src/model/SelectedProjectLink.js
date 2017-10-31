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
        current = projectLinkCollection.getByLinkId(ids);
      }
      eventbus.trigger('projectLink:clicked', get());
    };

    var orderSplitParts = function(links) {
      var splitLinks =  _.partition(links, function(link){
        return link.roadLinkSource === LinkGeomSource.SuravageLinkInterface.value && !_.isUndefined(link.connectedLinkId);
      });
      return _.sortBy(splitLinks[0], [
        function (s) {
          return (_.isUndefined(s.points) || _.isUndefined(s.points[0])) ? Infinity : s.points[0].y;},
        function (s) {
          return (_.isUndefined(s.points) || _.isUndefined(s.points[0])) ? Infinity : s.points[0].x;}]);
    };

    var openSplit = function (linkid, multiSelect) {
      if (!multiSelect) {
        current = projectLinkCollection.getByLinkId([linkid]);
        ids = [linkid];
      } else {
        ids = projectLinkCollection.getMultiSelectIds(linkid);
        current = projectLinkCollection.getByLinkId(ids);
      }
      var orderedSplitParts = orderSplitParts(get());
      var suravageA = orderedSplitParts[0];
      var suravageB = orderedSplitParts[1];
      suravageA.marker = "A";
      suravageB.marker = "B";
      eventbus.trigger('split:projectLinks', [suravageA, suravageB]);
    };

    var splitSuravageLink = function(suravage, split, mousePoint) {
      splitSuravageLinks(suravage, split, mousePoint, function(splitSuravageLinks) {
        var selection = [splitSuravageLinks.created, splitSuravageLinks.existing];
        if (!_.isUndefined(splitSuravageLinks.created.connectedLinkId)) {
          // Re-split with a new split point
          ids = projectLinkCollection.getMultiSelectIds(splitSuravageLinks.created.linkId);
          current = projectLinkCollection.getByLinkId(_.flatten(ids));
          var orderedSplitParts = orderSplitParts(get());
          var orderedPreviousSplit = orderSplitParts(selection);
          var suravageA = orderedSplitParts[0];
          var suravageB = orderedSplitParts[1];
          suravageA.marker = "A";
          suravageB.marker = "B";
          suravageA.points = orderedPreviousSplit[0].points;
          suravageB.points = orderedPreviousSplit[1].points;
          suravageA.splitPoint = mousePoint;
          suravageB.splitPoint = mousePoint;
          var measureLeft = calculateMeasure(suravageA);
          var measureRight = calculateMeasure(suravageB);
          suravageA.startMValue = 0;
          suravageA.endMValue = measureLeft;
          suravageB.startMValue = measureLeft;
          suravageB.endMValue = measureLeft + measureRight;
          eventbus.trigger('split:projectLinks', [suravageA, suravageB]);
        } else {
          eventbus.trigger('split:projectLinks', selection);
        }
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
