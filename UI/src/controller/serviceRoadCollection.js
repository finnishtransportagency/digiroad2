(function(root) {
  root.ServiceRoadCollection = function(backend, verificationCollection, spec) {
    LinearAssetsCollection.call(this, backend, verificationCollection, spec);
    this.linearAssets = [];
    var typeId = spec.typeId;
    var multiElementEventCategory = spec.multiElementEventCategory;
    var hasMunicipalityValidation = spec.hasMunicipalityValidation;
    var self = this;

    var multiElementEvent = function (eventName) {
      return multiElementEventCategory + ':' + eventName;
    };

    var generateUnknownLimitId = function(linearAsset) {
      return linearAsset.linkId.toString() +
        linearAsset.startMeasure.toFixed(2) +
        linearAsset.endMeasure.toFixed(2);
    };

    this.fetch = function(boundingBox, center, zoom) {
      return fetch(boundingBox, backend.getServiceRoadAssets(boundingBox, typeId, applicationModel.getWithRoadAddress(), zoom), center);
    };

    this.fetchAssetsWithComplementary = function(boundingBox, center, zoom) {
      return fetch(boundingBox, backend.getServiceRoadAssetsWithComplementary(boundingBox, typeId, applicationModel.getWithRoadAddress(), zoom), center);
    };

    var fetch = function(boundingBox, assets, center) {
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
        self.linearAssets = knownLinearAssets.concat(unknownLinearAssets);
        eventbus.trigger(multiElementEvent('fetched'), self.getAll());
        verificationCollection.fetch(boundingBox, center, typeId, hasMunicipalityValidation);
      });
    };
  };
})(this);
