(function(root) {
  root.LaneModellingCollection = function(backend, verificationCollection, spec) {
    LinearAssetsCollection.call(this, backend, verificationCollection, spec);
    var isWalkingCyclingActive = false;
    var isGeometryActive = true;
    var self = this;

    self.fetch = function(boundingBox, center, zoom) {
      if (isGeometryActive)
        return self.fetchAssets(boundingBox, backend.getLanesByBoundingBox(boundingBox, zoom, isWalkingCyclingActive), center);
    };

    self.activeWalkingCycling = function(enable) {
      isWalkingCyclingActive = enable;
    };

    self.activeGeometry = function(enable) {
      isGeometryActive = enable;
    };

    self.getGroup = function(segment) {
      return _.find(self.linearAssets, function(linearAssetGroup) {
        return _.some(linearAssetGroup, function(la) {
          var laneLaneCode = _.head(Property.getPropertyByPublicId(la.properties, 'lane_code').values).value;
          var segmentLaneCode = _.head(Property.getPropertyByPublicId(segment.properties, 'lane_code').values).value;
          return la.linkId == segment.linkId && laneLaneCode == segmentLaneCode && la.sideCode == segment.sideCode;});
      });
    };

    self.getMainLaneByLinkIdAndSideCode = function(linkId, sideCode) {
      return _.find(_.flatten(self.linearAssets), function(lane) {
          var laneCode = _.head(Property.getPropertyByPublicId(lane.properties, 'lane_code').values).value;
          return lane.linkId === linkId && laneCode === 1 && lane.sideCode === sideCode;
      });
    };

    self.fetchViewOnlyLanes = function(boundingBox, zoom) {
      return backend.getViewOnlyLanesByBoundingBox(boundingBox, zoom, isWalkingCyclingActive).then(function(lanes) {
        eventbus.trigger('fetchedViewOnly', lanes);
      });
    };

    self.splitLinearAsset = function(lane, split, callback) {
      var left = _.cloneDeep(lane);
      left.points = split.firstSplitVertices;
      left.endMeasure = split.splitMeasure;

      var right = _.cloneDeep(lane);
      right.points = split.secondSplitVertices;
      right.startMeasure = split.splitMeasure;

      if (left.startMeasure < right.startMeasure) {
        self.splitLinearAssets.created = left;
        self.splitLinearAssets.existing = right;
      } else {
        self.splitLinearAssets.created = right;
        self.splitLinearAssets.existing = left;
      }

      self.splitLinearAssets.created.id = 0;
      self.splitLinearAssets.existing.id = 0;

      self.dirty = true;
      callback(self.splitLinearAssets);
    };
  };
})(this);
