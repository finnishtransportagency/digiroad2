(function(root) {
  root.SpeedLimitsCollection = function(backend, verificationCollection) {
    var speedLimits = [];
    var speedLimitsHistory = [];
    var dirty = false;
    var selection = null;
    var self = this;
    var splitSpeedLimits = {};
    var separatedLimit = {};
    var isComplementaryActive = false;

    var maintainSelectedSpeedLimitChain = function(collection) {
      if (!selection) return collection;

      var isSelected = function (speedLimit) { return selection.isSelected(speedLimit); };

      var collectionPartitionedBySelection = _.groupBy(collection, function(speedLimitGroup) {
        return _.some(speedLimitGroup, isSelected);
      });
      var groupContainingSelection = _.flatten(collectionPartitionedBySelection[true] || []);

      var collectionWithoutGroup = collectionPartitionedBySelection[false] || [];
      var groupWithoutSelection = _.reject(groupContainingSelection, isSelected);

      return collectionWithoutGroup.concat(_.isEmpty(groupWithoutSelection) ? [] : [groupWithoutSelection]).concat([selection.get()]);
    };

    var filterComplementaries = function (speedLimits) {
      if(isComplementaryActive)
         return speedLimits;
      return _.map(speedLimits, function(speedLimitFilter) { return _.filter(speedLimitFilter, function(asset) { return asset.linkSource != 2; });});
    };

    this.getAll = function() {
      return maintainSelectedSpeedLimitChain(filterComplementaries(speedLimits));
    };

    this.getAllHistory = function() {
      return maintainSelectedSpeedLimitChain(speedLimitsHistory);
    };

    var generateUnknownLimitId = function(speedLimit) {
      return speedLimit.linkId.toString() +
          speedLimit.startMeasure.toFixed(2) +
          speedLimit.endMeasure.toFixed(2);
    };

    this.fetch = function(boundingBox) {
      return backend.getSpeedLimits(boundingBox, applicationModel.getWithRoadAddress()).then(function(speedLimitGroups) {
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
        verificationCollection.fetch(boundingBox, 20);
      });
    };

    this.fetchHistory = function (boundingbox) {
      return backend.getSpeedLimitsHistory(boundingbox).then(function(speedLimitHistoryGroups) {
        var partitionedSpeedLimitHistoryGroups = _.groupBy(speedLimitHistoryGroups, function(speedLimitHistoryGroup) {
              return _.some(speedLimitHistoryGroup, function(speedLimitHistory) { return _.has(speedLimitHistory, "id"); });
        });
        var knownHistorySpeedLimits = partitionedSpeedLimitHistoryGroups[true] || [];
        var unknownHistorySpeedLimits = _.map(partitionedSpeedLimitHistoryGroups[false], function(speedLimitHistoryGroup) {
              return _.map(speedLimitHistoryGroup, function(speedLimitHistory) {
                return _.merge({}, speedLimitHistory, { generatedId: generateUnknownLimitId(speedLimitHistory) });
              });
            }) || [];
        speedLimitsHistory = knownHistorySpeedLimits.concat(unknownHistorySpeedLimits);
        eventbus.trigger('speedLimits:drawSpeedLimitsHistory', self.getAllHistory());
      });
    };

    this.hideHistorySpeedLimits = function () {
      eventbus.trigger('speedLimits:hideSpeedLimitsHistory');
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
        return [point.x, point.y];
      });
      return new ol.geom.LineString(points).getLength();
    };

    this.splitSpeedLimit = function(id, split, callback) {
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

      splitSpeedLimits.created.marker = 'A';
      splitSpeedLimits.existing.marker = 'B';

      dirty = true;
      callback(splitSpeedLimits);
      eventbus.trigger('speedLimits:fetched', self.getAll());
    };

    this.saveSplit = function(callback) {
      backend.splitSpeedLimit(splitSpeedLimits.existing.id, splitSpeedLimits.splitMeasure, splitSpeedLimits.created.value, splitSpeedLimits.existing.value, function() {
        eventbus.trigger('speedLimit:saved');
        splitSpeedLimits = {};
        dirty = false;
        callback();
      }, function() {
        eventbus.trigger('asset:updateFailed');
      });
    };

    this.saveSeparation = function(callback) {
      backend.separateSpeedLimit(separatedLimit.A.id, separatedLimit.A.value, separatedLimit.B.value, function() {
        eventbus.trigger('speedLimit:saved');
        separatedSpeedLimit = {};
        dirty = false;
        callback();
      }, function() {
        eventbus.trigger('asset:updateFailed');
      });
    };

    this.cancelCreation = function() {
      dirty = false;
      splitSpeedLimits = {};
      separatedSpeedLimit = {};
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

    this.activeComplementary = function(enable) {
      isComplementaryActive = enable;
    };

    function complementaryIsActive() {
       return isComplementaryActive;
    }

  };
})(this);
