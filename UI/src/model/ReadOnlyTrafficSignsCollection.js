(function(root) {
  root.ReadOnlyTrafficSignsCollection = function (backend, layerName, allowComplementary) {

    PointAssetsCollection.call(this);
    var me = this;

    var trafficSignsShowing = {
      speedLimit: false,
      trSpeedLimits: false,
      totalWeightLimit: false,
      trailerTruckWeightLimit: false,
      axleWeightLimit: false,
      bogieWeightLimit: false,
      heightLimit: false,
      widthLimit: false,
      lengthLimit: false
    };

    var trafficSignValues = {
      speedLimit: [1, 2, 3, 4, 5, 6],
      trSpeedLimits: [1, 2, 3, 4, 5, 6],
      totalWeightLimit: [32],
      trailerTruckWeightLimit: [33],
      axleWeightLimit: [34],
      bogieWeightLimit: [35],
      heightLimit: [31],
      widthLimit: [30],
      lengthLimit: [8]
    };

    var filterTrafficSigns = function (assets) {
      return _.filter(assets, function (asset) {
        var existingValue = _.first(_.find(asset.propertyData, function (prop) {
          return prop.publicId === "trafficSigns_type";
        }).values);
        if (!existingValue)
          return false;
        return _.contains(getTrafficSignsToShow(), parseInt(existingValue.propertyValue));
      });
    };

    this.setTrafficSigns = function (trafficSign, isShowing) {
      if (trafficSignsShowing[trafficSign] !== isShowing) {
        trafficSignsShowing[trafficSign] = isShowing;
        eventbus.trigger('trafficSigns:signsChanged', getTrafficSignsToShow());
      }
    };

    var getTrafficSignsToShow = function () {
      var signsToShow = [];
      _.forEach(trafficSignsShowing, function (isShowing, trafficSign) {
        if (isShowing)
          signsToShow = signsToShow.concat(trafficSignValues[trafficSign]);
      });
      return signsToShow;
    };

    this.fetch = function (boundingBox) {
      return backend.getPointAssetsWithComplementary(boundingBox, layerName)
        .then(function (assets) {
          eventbus.trigger('pointAssets:fetched');
          me.allowComplementaryIsActive(allowComplementary);
          return filterTrafficSigns(me.filterComplementaries(assets));
        });
    };
  };
})(this);
