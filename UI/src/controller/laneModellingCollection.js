(function(root) {
  root.LaneModellingCollection = function(backend, verificationCollection, spec) {
    LinearAssetsCollection.call(this, backend, verificationCollection, spec);
    var multiElementEventCategory = spec.multiElementEventCategory;
    this.linearAssets = [];
    var dirty = false;
    var self = this;
    var splitLaneAssets = {};

    var multiElementEvent = function (eventName) {
      return multiElementEventCategory + ':' + eventName;
    };

    var generateUnknownLimitId = function(laneAsset) {
      return laneAsset.linkId.toString() +
        laneAsset.startMeasure.toFixed(2) +
        laneAsset.endMeasure.toFixed(2);
    };

    this.fetch = function(boundingBox, center, zoom) {
      return fetch(boundingBox, backend.getLanesByBoundingBox(boundingBox, zoom));
    };

    this.fetchAssetsWithComplementary = function(boundingBox, center, zoom) {
      return fetch(boundingBox, backend.getLanesWithComplementaryByBoundingBox(boundingBox, zoom));
    };

    var fetch = function(boundingBox, assets) {
      return assets.then(function(laneAssetGroups) {
        var partitionedLaneAssetGroups = _.groupBy(laneAssetGroups, function(laneAssetGroup) {
          return _.some(laneAssetGroup, function(laneAsset) { return _.has(laneAsset, 'value'); });
        });
        var knownLaneAssets = partitionedLaneAssetGroups[true] || [];
        var unknownLaneAssets = _.map(partitionedLaneAssetGroups[false], function(laneAssetGroup) {
          return _.map(laneAssetGroup, function(laneAsset) {
            return _.merge({}, laneAsset, { generatedId: generateUnknownLimitId(laneAsset) });
          });
        }) || [];
        self.linearAssets = knownLaneAssets.concat(unknownLaneAssets);
        eventbus.trigger(multiElementEvent('fetched'), self.getAll());
      });
    };

    var isEqual = function(a, b) {
      function equalUnknown() {
        return (_.has(a, 'generatedId') && _.has(b, 'generatedId') && (a.generatedId === b.generatedId));
      }

      function equalExisting() {
        return (!_.isUndefined(a.id) && !_.isUndefined(b.id) && (a.id === b.id));
      }

      return equalUnknown() || equalExisting();
    };

    this.getGroup = function(segment) {
      return _.find(self.linearAssets, function(linearAssetGroup) {
        return _.some(linearAssetGroup, function(s) { return s.linkId == segment.linkId; });
      });
    };

    var replaceOneSegment = function(collection, segment, newSegment) {
      var collectionPartitionedBySegment = _.groupBy(collection, function(linearAssetGroup) {
        return _.some(linearAssetGroup, function(s) {
          return isEqual(s, segment);
        });
      });
      var groupContainingSegment = _.flatten(collectionPartitionedBySegment[true] || []);

      var collectionWithoutGroup = collectionPartitionedBySegment[false] || [];
      var groupWithoutSegment = _.reject(groupContainingSegment, function(s) { return isEqual(s, segment); });

      return collectionWithoutGroup.concat(_.map(groupWithoutSegment, function(s) { return [s]; })).concat([[newSegment]]);
    };

    this.replaceSegments = function(selection, lane, newSegments) {
      self.linearAssets = replaceOneSegment(self.linearAssets, lane, newSegments);
      return newSegments;
    };

    var calculateMeasure = function(link) {
      var points = _.map(link.points, function(point) {
        return [point.x, point.y];
      });
      return new ol.geom.LineString(points).getLength();
    };

    this.splitLinearAsset = function(lane, split, callback) {
      var left = _.cloneDeep(lane);
      left.points = split.firstSplitVertices;
      left.endMeasure = split.splitMeasure;

      var right = _.cloneDeep(lane);
      right.points = split.secondSplitVertices;
      right.startMeasure = split.splitMeasure;

      if (calculateMeasure(left) < calculateMeasure(right)) {
        splitLaneAssets.created = left;
        splitLaneAssets.existing = right;
      } else {
        splitLaneAssets.created = right;
        splitLaneAssets.existing = left;
      }

      splitLaneAssets.created.id = 0;
      splitLaneAssets.existing.id = 0;

      splitLaneAssets.created.marker = 'A';
      splitLaneAssets.existing.marker = 'B';

      dirty = true;
      callback(splitLaneAssets);
    };
  };
})(this);
