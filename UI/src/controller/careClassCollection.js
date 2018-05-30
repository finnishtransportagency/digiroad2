(function(root) {
  root.CareClassCollection = function(backend, verificationCollection, spec) {
    var typeId = spec.typeId;
    var multiElementEventCategory = spec.multiElementEventCategory;
    var hasMunicipalityValidation = spec.hasMunicipalityValidation;
    this.linearAssets = [];
    var selection = null;
    var self = this;


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
      return maintainSelectedLinearAssetChain(self.linearAssets);
    };

    var generateUnknownLimitId = function(linearAsset) {
      return linearAsset.linkId.toString() +
        linearAsset.startMeasure.toFixed(2) +
        linearAsset.endMeasure.toFixed(2);
    };

    this.fetch = function (boundingBox, center) {
      return fetch(boundingBox, backend.getLinearAssets(boundingBox, typeId, applicationModel.getWithRoadAddress()), center);
    };

    this.fetchAssetsWithComplementary = function (boundingBox, center) {
      return fetch(boundingBox, backend.getLinearAssetsWithComplementary(boundingBox, typeId, applicationModel.getWithRoadAddress()), center);
    };

    var fetch = function (boundingBox, assets, center) {
      return assets.then(function (linearAssetGroups) {
        var filteredGroups = _.map(linearAssetGroups, function(group){
          return _.reject(group, function(asset){
            return asset.administrativeClass === 3;
          })
        });
        var partitionedLinearAssetGroups = _.groupBy(filteredGroups, function (linearAssetGroup) {
          return _.some(linearAssetGroup, function (linearAsset) {
            return _.has(linearAsset, 'value');
          });
        });
        var knownLinearAssets = partitionedLinearAssetGroups[true] || [];
        var unknownLinearAssets = _.map(partitionedLinearAssetGroups[false], function (linearAssetGroup) {
          return _.map(linearAssetGroup, function (linearAsset) {
            return _.merge({}, linearAsset, {generatedId: generateUnknownLimitId(linearAsset)});
          });
        }) || [];
        self.linearAssets = knownLinearAssets.concat(unknownLinearAssets);
        eventbus.trigger(multiElementEvent('fetched'), self.getAll());
        verificationCollection.fetch(boundingBox, center, typeId, hasMunicipalityValidation);
      });
    };

  }
})(this);
