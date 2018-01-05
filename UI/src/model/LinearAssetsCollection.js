(function(root) {
  root.LinearAssetsCollection = function(backend, verificationCollection, typeId, singleElementEventCategory, multiElementEventCategory) {
      var linearAssets = [];
      var dirty = false;
      var selection = null;
      var self = this;
      var splitLinearAssets = {};
      var separatedLimit = {};

      var singleElementEvent = function (eventName) {
          return singleElementEventCategory + ':' + eventName;
      };

      var multiElementEvent = function (eventName) {
          return multiElementEventCategory + ':' + eventName;
      };

      var maintainSelectedLinearAssetChain = function (collection) {
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
          return maintainSelectedLinearAssetChain(linearAssets);
      };

      this.getById = function (Id) {
          return _.find(_.flatten(linearAssets), {id: Id});
      };

    var generateUnknownLimitId = function(linearAsset) {
      return linearAsset.linkId.toString() +
          linearAsset.startMeasure.toFixed(2) +
          linearAsset.endMeasure.toFixed(2);
    };

    this.fetch = function(boundingBox) {
      return fetch(boundingBox, backend.getLinearAssets(boundingBox, typeId, applicationModel.getWithRoadAddress()));
    };

    this.fetchAssetsWithComplementary = function(boundingBox) {
      return fetch(boundingBox, backend.getLinearAssetsWithComplementary(boundingBox, typeId));
    };

    this.fetchReadOnlyAssets = function(boundingBox) {
      return fetchReadOnly(boundingBox, backend.getReadOnlyLinearAssets(boundingBox, typeId));
    };

    this.fetchReadOnlyAssetsWithComplementary = function(boundingBox) {
      return fetchReadOnly(boundingBox, backend.getReadOnlyLinearAssetsComplementaries(boundingBox, typeId));
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

    var fetch = function(boundingBox, assets) {
      return assets.then(function(linearAssetGroups) {
        var partitionedLinearAssetGroups = _.groupBy(linearAssetGroups, function(linearAssetGroup) {
          return _.some(linearAssetGroup, function(linearAsset) { return _.has(linearAsset, 'value'); });
        });
        var knownLinearAssets = partitionedLinearAssetGroups[true] || [];
        var unknownLinearAssets = _.map(partitionedLinearAssetGroups[false], function(linearAssetGroup) {
            return _.map(linearAssetGroup, function(linearAsset) {
              return _.merge({}, linearAsset, { generatedId: generateUnknownLimitId(linearAsset) });
            });
          }) || [];
        linearAssets = knownLinearAssets.concat(unknownLinearAssets);
        eventbus.trigger(multiElementEvent('fetched'), self.getAll());
        verificationCollection.fetch(boundingBox, typeId);
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
      return _.find(linearAssets, function(linearAssetGroup) {
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

    this.replaceSegments = function(selection, newSegments) {
      if (splitLinearAssets.created) {
        splitLinearAssets.created.value = newSegments[0].value;
      }
      else if (selection.length === 1) {
        linearAssets = replaceOneSegment(linearAssets, selection[0], newSegments[0]);
      } else {
        linearAssets = replaceGroup(linearAssets, selection[0], newSegments);
      }
      return newSegments;
    };

    var calculateMeasure = function(link) {
      var points = _.map(link.points, function(point) {
        return [point.x, point.y];
      });
      return new ol.geom.LineString(points).getLength();
    };

    this.splitLinearAsset = function(id, split, callback) {
      var link = _.find(_.flatten(linearAssets), { id: id });

      var left = _.cloneDeep(link);
      left.points = split.firstSplitVertices;

      var right = _.cloneDeep(link);
      right.points = split.secondSplitVertices;

      if (calculateMeasure(left) < calculateMeasure(right)) {
        splitLinearAssets.created = left;
        splitLinearAssets.existing = right;
      } else {
        splitLinearAssets.created = right;
        splitLinearAssets.existing = left;
      }

      splitLinearAssets.created.id = null;
      splitLinearAssets.splitMeasure = split.splitMeasure;

      splitLinearAssets.created.marker = 'A';
      splitLinearAssets.existing.marker = 'B';

      dirty = true;
      callback(splitLinearAssets);
      eventbus.trigger(multiElementEvent('fetched'), self.getAll());
    };

    this.saveSplit = function(callback) {
      backend.splitLinearAssets(typeId, splitLinearAssets.existing.id, splitLinearAssets.splitMeasure, splitLinearAssets.created.value, splitLinearAssets.existing.value, function() {
        eventbus.trigger(singleElementEvent('saved'));
        splitLinearAssets = {};
        dirty = false;
        callback();
      }, function() {
        eventbus.trigger('asset:updateFailed');
      });
    };

    this.saveSeparation = function(callback) {
      var success = function() {
        eventbus.trigger(singleElementEvent('saved'));
        dirty = false;
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
      dirty = false;
      splitLinearAssets = {};
      eventbus.trigger(multiElementEvent('cancelled'), self.getAll());
    };

    this.isDirty = function() {
      return dirty;
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
      dirty = true;

      separatedLimit.A = limitA;
      separatedLimit.B = limitB;
      return [limitA, limitB];
    };
  };
})(this);
