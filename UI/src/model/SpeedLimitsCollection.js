(function(root) {
  root.SpeedLimitsCollection = function(backend) {
    var speedLimits = [];
    var dirty = false;
    var selection = null;
    var self = this;
    var splitSpeedLimits = {};
    var separatedLimit = {};

    var maintainSelectedSpeedLimitChain = function(collection) {
      if (!selection) return collection;

      var isSelected = function (speedLimit) { return selection.isSelected(speedLimit); };

      var collectionPartitionedBySelection = _.groupBy(collection, function(speedLimitGroup) {
        return _.some(speedLimitGroup, isSelected);
      });
      var groupContainingSelection = _.flatten(collectionPartitionedBySelection[true] || []);

      var collectionWithoutGroup = collectionPartitionedBySelection[false] || [];
      var groupWithoutSelection = _.reject(groupContainingSelection, isSelected);

      return collectionWithoutGroup.concat([groupWithoutSelection]).concat([selection.get()]);
    };

    var handleSplit = function(collection) {
      var existingSplit = _.has(splitSpeedLimits, 'existing') ? [splitSpeedLimits.existing] : [];
      var createdSplit = _.has(splitSpeedLimits, 'created') ? [splitSpeedLimits.created] : [];
      return _.map(collection, function(group) { return _.reject(group, { id: splitSpeedLimits.existing.id }); })
          .concat(existingSplit)
          .concat(createdSplit);
    };

    this.getAll = function() {
      var allWithSelectedSpeedLimitChain = maintainSelectedSpeedLimitChain(speedLimits);

      if (selection && selection.isSplit()) {
        return handleSplit(allWithSelectedSpeedLimitChain);
      } else {
        return allWithSelectedSpeedLimitChain;
      }
    };

    var generateUnknownLimitId = function(speedLimit) {
      return speedLimit.mmlId.toString() +
          speedLimit.startMeasure.toFixed(2) +
          speedLimit.endMeasure.toFixed(2);
    };

    this.fetch = function(boundingBox) {
      backend.getSpeedLimits(boundingBox, function(speedLimitGroups) {
        var partitionedSpeedLimitGroups = _.groupBy(speedLimitGroups, function(speedLimitGroup) {
          return _.some(speedLimitGroup, function(speedLimit) { return _.has(speedLimit, "id"); });
        });
        var knownSpeedLimits = partitionedSpeedLimitGroups[true] || [];
        var unknownSpeedLimits = _.map(partitionedSpeedLimitGroups[false], function(speedLimitGroup) {
          return _.map(speedLimitGroup, function(speedLimit) {
            return _.merge({}, speedLimit, { generatedId: generateUnknownLimitId(speedLimit) });
          });
        }) || [];
        speedLimits = knownSpeedLimits.concat(unknownSpeedLimits);
        eventbus.trigger('speedLimits:fetched', self.getAll());
      });
    };

    var isUnknown = function(speedLimit) {
      return !_.has(speedLimit, 'id');
    };

    var isEqual = function(a, b) {
      return (_.has(a, 'generatedId') && _.has(b, 'generatedId') && (a.generatedId === b.generatedId)) ||
        ((!isUnknown(a) && !isUnknown(b)) && (a.id === b.id));
    };

    this.getGroup = function(segment) {
      return _.find(speedLimits, function(speedLimitGroup) {
        return _.some(speedLimitGroup, function(s) { return isEqual(s, segment); });
      });
    };

    this.setSelection = function(sel) {
      selection = sel;
    };

    var replaceGroup = function(collection, segment, newGroup) {
      return _.reject(collection, function(speedLimitGroup) {
        return _.some(speedLimitGroup, function(s) {
          return isEqual(s, segment);
        });
      }).concat([newGroup]);
    };

    var replaceOneSegment = function(collection, segment, newSegment) {
      var collectionPartitionedBySegment = _.groupBy(collection, function(speedLimitGroup) {
        return _.some(speedLimitGroup, function(s) {
          return isEqual(s, segment);
        });
      });
      var groupContainingSegment = _.flatten(collectionPartitionedBySegment[true] || []);

      var collectionWithoutGroup = collectionPartitionedBySegment[false] || [];
      var groupWithoutSegment = _.reject(groupContainingSegment, function(s) { return isEqual(s, segment); });

      return collectionWithoutGroup.concat(_.map(groupWithoutSegment, function(s) { return [s]; })).concat([[newSegment]]);
    };

    this.replaceSegments = function(selection, newSegments) {
      if (splitSpeedLimits.created) {
        splitSpeedLimits.created.value = newSegments[0].value;
      }
      else if (selection.length === 1) {
        speedLimits = replaceOneSegment(speedLimits, selection[0], newSegments[0]);
      } else {
        speedLimits = replaceGroup(speedLimits, selection[0], newSegments);
      }
      return newSegments;
    };

    var calculateMeasure = function(link) {
      var points = _.map(link.points, function(point) {
        return new OpenLayers.Geometry.Point(point.x, point.y);
      });
      return new OpenLayers.Geometry.LineString(points).getLength();
    };

    this.splitSpeedLimit = function(id, mmlId, split, callback) {
      var link = _.find(_.flatten(speedLimits), { id: id });

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
      splitSpeedLimits.splitMmlId = mmlId;
      dirty = true;
      callback(splitSpeedLimits.created);
      eventbus.trigger('speedLimits:fetched', self.getAll());
    };

    this.saveSplit = function(callback) {
      backend.splitSpeedLimit(splitSpeedLimits.existing.id, splitSpeedLimits.splitMmlId, splitSpeedLimits.splitMeasure, splitSpeedLimits.created.value, function(updatedSpeedLimits) {
        var existingId = splitSpeedLimits.existing.id;
        splitSpeedLimits = {};
        dirty = false;

        var speedLimitsPartitionedBySplitGroup = _.groupBy(speedLimits, function(group) { return _.some(group, { id: existingId }); });
        var splitGroupLimits = _.chain(speedLimitsPartitionedBySplitGroup[true] || [])
          .flatten()
          .reject(function(x) { return _.some(updatedSpeedLimits, { id: x.id }); })
          .concat(updatedSpeedLimits)
          .map(function(x) { return [x]; })
          .value();

        speedLimits = (speedLimitsPartitionedBySplitGroup[false] || []).concat(splitGroupLimits);

        var newSpeedLimit = _.find(updatedSpeedLimits, function(speedLimit) { return speedLimit.id !== existingId; });
        callback(newSpeedLimit);

        eventbus.trigger('speedLimit:saved');
      }, function() {
        eventbus.trigger('asset:updateFailed');
      });
    };

    this.saveSeparation = function(callback) {
      backend.separateSpeedLimit(separatedLimit.A.id, separatedLimit.A.value, separatedLimit.B.value, function() {
        eventbus.trigger('speedLimit:saved');
        dirty = false;
        callback();
      }, function() {
        eventbus.trigger('asset:updateFailed');
      });
    };

    this.cancelCreation = function() {
      dirty = false;
      splitSpeedLimits = {};
      eventbus.trigger('speedLimits:fetched', self.getAll());
    };

    this.isDirty = function() {
      return dirty;
    };

    this.separateSpeedLimit = function(id) {
      var originalLimit = _.find(_.flatten(speedLimits), { id: id });
      var limitA = _.cloneDeep(originalLimit);
      var limitB = _.cloneDeep(originalLimit);

      limitA.sideCode = 2;
      limitA.marker = 'A';
      limitB.sideCode = 3;
      limitB.marker = 'B';
      limitB.id = null;
      dirty = true;

      separatedLimit.A = limitA;
      separatedLimit.B = limitB;
      return [limitA, limitB];
    };

  };
})(this);
