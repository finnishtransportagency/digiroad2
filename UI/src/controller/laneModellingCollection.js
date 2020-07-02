(function(root) {
  root.LaneModellingCollection = function(backend, verificationCollection, spec) {
    LinearAssetsCollection.call(this, backend, verificationCollection, spec);
    var self = this;

    self.fetch = function(boundingBox, center, zoom) {
      return self.fetchAssets(boundingBox, backend.getLanesByBoundingBox(boundingBox, zoom), center);
    };

    self.getGroup = function(segment) {
      return _.find(self.linearAssets, function(linearAssetGroup) {
        return _.some(linearAssetGroup, function(la) {
          var laneLaneCode = _.head(Property.getPropertyByPublicId(la.value, 'lane_code').values).value;
          var segmentLaneCode = _.head(Property.getPropertyByPublicId(segment.value, 'lane_code').values).value;
          return la.linkId == segment.linkId && laneLaneCode == segmentLaneCode;});
      });
    };

    self.splitLinearAsset = function(lane, split, callback) {
      var left = _.cloneDeep(lane);
      left.points = split.firstSplitVertices;
      left.endMeasure = split.splitMeasure;

      var right = _.cloneDeep(lane);
      right.points = split.secondSplitVertices;
      right.startMeasure = split.splitMeasure;

      if (self.calculateMeasure(left) < self.calculateMeasure(right)) {
        self.splitLinearAssets.created = left;
        self.splitLinearAssets.existing = right;
      } else {
        self.splitLinearAssets.created = right;
        self.splitLinearAssets.existing = left;
      }

      self.splitLinearAssets.created.id = 0;
      self.splitLinearAssets.existing.id = 0;

      self.splitLinearAssets.created.marker = 'A';
      self.splitLinearAssets.existing.marker = 'B';

      self.dirty = true;
      callback(self.splitLinearAssets);
    };
  };
})(this);
