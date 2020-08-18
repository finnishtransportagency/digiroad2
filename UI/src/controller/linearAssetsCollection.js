(function(root) {
  root.LinearAssetsCollection = function(backend, verificationCollection, spec) {
      var typeId = spec.typeId;
      var singleElementEventCategory = spec.singleElementEventCategory;
      var multiElementEventCategory = spec.multiElementEventCategory;
      var hasMunicipalityValidation = spec.hasMunicipalityValidation;
      this.linearAssets = [];
      this.dirty = false;
      var selection = null;
      var self = this;
      this.splitLinearAssets = {};
      var separatedLimit = {};

      var singleElementEvent = function (eventName) {
          return singleElementEventCategory + ':' + eventName;
      };

      this.multiElementEvent = function (eventName) {
          return multiElementEventCategory + ':' + eventName;
      };

    this.maintainSelectedLinearAssetChain = function (collection) {
      if (!selection) return collection;

      var isSelected = function (linearAsset) { return selection.isSelected(linearAsset); };

      var collectionPartitionedBySelection = _.groupBy(collection, function (linearAssetGroup) {
        return _.some(linearAssetGroup, isSelected);
      });
      var groupContainingSelection = _.flatten(collectionPartitionedBySelection[true] || []);

      var collectionWithoutGroup = collectionPartitionedBySelection[false] || [];
      var groupWithoutSelection = _.reject(groupContainingSelection, isSelected);

      return collectionWithoutGroup.concat(_.isEmpty(groupWithoutSelection) ? [] : [groupWithoutSelection]).concat([selection.get()]);
    };

      this.getAll = function () {
          return self.maintainSelectedLinearAssetChain(self.linearAssets);
      };

      this.getById = function (Id) {
          return _.find(_.flatten(self.linearAssets), {id: Id});
      };

    this.generateUnknownLimitId = function(linearAsset) {
      if (!_.isUndefined(linearAsset.startMeasure) && !_.isUndefined(linearAsset.endMeasure)){
        return linearAsset.linkId.toString() +
            linearAsset.startMeasure.toFixed(2) +
            linearAsset.endMeasure.toFixed(2);
      } else
        return linearAsset.linkId.toString() + linearAsset.mmlId.toString();
    };

    this.fetch = function(boundingBox, center, zoom) {
      return self.fetchAssets(boundingBox, backend.getLinearAssets(boundingBox, typeId, applicationModel.getWithRoadAddress(), zoom), center);
    };

    this.fetchAssetsWithComplementary = function(boundingBox, center, zoom) {
      return self.fetchAssets(boundingBox, backend.getLinearAssetsWithComplementary(boundingBox, typeId, applicationModel.getWithRoadAddress(), zoom), center);
    };

    this.fetchReadOnlyAssets = function(boundingBox) {
      return fetchReadOnly(boundingBox, backend.getReadOnlyLinearAssets(boundingBox, typeId, applicationModel.getWithRoadAddress()));
    };

    this.fetchReadOnlyAssetsWithComplementary = function(boundingBox) {
      return fetchReadOnly(boundingBox, backend.getReadOnlyLinearAssetsComplementaries(boundingBox, typeId, applicationModel.getWithRoadAddress()));
    };

    var fetchReadOnly = function(boundingBox, assets) {
      return assets.then(function(linearAssetGroups) {
        var partitionedLinearAssetGroups = _.groupBy(linearAssetGroups, function(linearAssetGroup) {
          return _.some(linearAssetGroup, function(linearAsset) { return _.has(linearAsset, 'values'); });
        });
        var knownLinearAssets = partitionedLinearAssetGroups[true] || [];
        eventbus.trigger('fetchedReadOnly', knownLinearAssets.concat([]));
      });
    };

    this.fetchAssets = function(boundingBox, assets, center) {
      return assets.then(function(linearAssetGroups) {
        var partitionedLinearAssetGroups = _.groupBy(linearAssetGroups, function(linearAssetGroup) {
          return _.some(linearAssetGroup, function(linearAsset) { return _.has(linearAsset, 'value'); });
        });
        var knownLinearAssets = partitionedLinearAssetGroups[true] || [];
        var unknownLinearAssets = _.map(partitionedLinearAssetGroups[false], function(linearAssetGroup) {
            return _.map(linearAssetGroup, function(linearAsset) {
              return _.merge({}, linearAsset, { generatedId: self.generateUnknownLimitId(linearAsset) });
            });
          }) || [];
        self.linearAssets = knownLinearAssets.concat(unknownLinearAssets);
        eventbus.trigger(self.multiElementEvent('fetched'), self.getAll());
        verificationCollection.fetch(boundingBox, center, typeId, hasMunicipalityValidation);
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
        return _.some(linearAssetGroup, function(s) { return isEqual(s, segment); });
      });
    };

    this.setSelection = function(sel) {
      selection = sel;
    };

    var replaceGroup = function(collection, segment, newGroup) {
      return _.reject(collection, function(linearAssetGroup) {
        return _.some(linearAssetGroup, function(s) {
          return isEqual(s, segment);
        });
      }).concat([newGroup]);
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

    this.replaceCreatedSplit = function(selection, newSegment) {
      self.splitLinearAssets.created = newSegment;
      separatedLimit.A = newSegment;
      return newSegment;
    };

    this.replaceExistingSplit = function(selection, existingSegment) {
      self.splitLinearAssets.existing = existingSegment;
      separatedLimit.B = existingSegment;
      return existingSegment;
    };

    this.replaceSegments = function(selection, newSegments) {
      if (self.splitLinearAssets.created) {
        self.splitLinearAssets.created.value = newSegments[0].value;
      }
      else if (selection.length === 1) {
        self.linearAssets = replaceOneSegment(self.linearAssets, selection[0], newSegments[0]);
      } else {
        self.linearAssets = replaceGroup(self.linearAssets, selection[0], newSegments);
      }
      return newSegments;
    };

    this.verifyLinearAssets = function(payload) {
      backend.verifyLinearAssets(payload, function () {
        eventbus.trigger(singleElementEvent('saved'));
      }, function () {
        eventbus.trigger('asset:verificationFailed');
      });
    };

    this.calculateMeasure = function(link) {
      var points = _.map(link.points, function(point) {
        return [point.x, point.y];
      });
      return new ol.geom.LineString(points).getLength();
    };

    this.splitLinearAsset = function(id, split, callback) {
      var link = _.find(_.flatten(self.linearAssets), { id: id });

      var left = _.cloneDeep(link);
      left.points = split.firstSplitVertices;

      var right = _.cloneDeep(link);
      right.points = split.secondSplitVertices;

      if (self.calculateMeasure(left) < self.calculateMeasure(right)) {
        self.splitLinearAssets.created = left;
        self.splitLinearAssets.existing = right;
      } else {
        self.splitLinearAssets.created = right;
        self.splitLinearAssets.existing = left;
      }

      self.splitLinearAssets.created.id = null;
      self.splitLinearAssets.splitMeasure = split.splitMeasure;

      self.splitLinearAssets.created.marker = 'A';
      self.splitLinearAssets.existing.marker = 'B';

      self.dirty = true;
      callback(self.splitLinearAssets);
      eventbus.trigger(self.multiElementEvent('fetched'), self.getAll());
    };

    this.saveSplit = function(callback) {
      backend.splitLinearAssets(typeId, self.splitLinearAssets.existing.id, self.splitLinearAssets.splitMeasure, self.splitLinearAssets.created.value, self.splitLinearAssets.existing.value, function() {
        eventbus.trigger(singleElementEvent('saved'));
        self.splitLinearAssets = {};
        self.dirty = false;
        callback();
      }, function() {
        eventbus.trigger('asset:updateFailed');
      });
    };

    this.saveSeparation = function(callback) {
      var success = function() {
        eventbus.trigger(singleElementEvent('saved'));
        self.dirty = false;
        callback();
      };
      var failure = function() {
        eventbus.trigger('asset:updateFailed');
      };
      separatedLimit.A = _.omit(separatedLimit.A, 'geometry');
      separatedLimit.B =_.omit(separatedLimit.B, 'geometry');
      if (separatedLimit.A.id) {
        backend.separateLinearAssets(typeId, separatedLimit.A.id, separatedLimit.A.value, separatedLimit.B.value, success, failure);
      } else {
        var separatedLimits = _.filter([separatedLimit.A, separatedLimit.B], function(limit) { return !_.isUndefined(limit.value); });
        backend.createLinearAssets({typeId: typeId, newLimits: separatedLimits}, success, failure);
      }
    };

    this.cancelCreation = function() {
      self.dirty = false;
      self.splitLinearAssets = {};
      eventbus.trigger(self.multiElementEvent('cancelled'), self.getAll());
    };

    this.isDirty = function() {
      return self.dirty;
    };

    this.separateLinearAsset = function(selectedLinearAsset) {
      var limitA = _.clone(selectedLinearAsset);
      var limitB = _.clone(selectedLinearAsset);

      limitA = _.omit(limitA, 'geometry');
      limitB = _.omit(limitB, 'geometry');
      limitA.sideCode = 2;
      limitA.marker = 'A';
      limitB.sideCode = 3;
      limitB.marker = 'B';
      limitB.id = null;
      self.dirty = true;

      separatedLimit.A = limitA;
      separatedLimit.B = limitB;
      return [limitA, limitB];
    };
  };
})(this);
