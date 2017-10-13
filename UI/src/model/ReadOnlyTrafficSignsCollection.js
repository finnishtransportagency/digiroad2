(function(root) {
  root.ReadOnlyTrafficSignsCollection = function(backend, layerName, allowComplementary) {

    PointAssetsCollection.call(this);

    var me = this;

    var trafficSignsShowing = {
      speedLimits: true, //[1, 2, 3, 4, 5, 6]
      pedestrianCrossings: false, //[7]
      maximumLengths: false, //[8]
      generalWarnings: false, //[9]
      turningRestrictions: false //[10, 11, 12]
    };

    var trafficSignValues = {
      speedLimits: [1, 2, 3, 4, 5, 6],
      pedestrianCrossings: [7],
      maximumLengths: [8],
      generalWarnings: [9],
      turningRestrictions: [10, 11, 12]
    };

    var filterTrafficSigns = function (asset) {
      return _.filter(asset, function (asset) {
        var existingValue = _.first(_.find(asset.propertyData, function(prop){return prop.publicId === "trafficSigns_type";}).values);
        if(!existingValue)
          return false;
        return _.contains(getTrafficSignsToShow(), parseInt(existingValue.propertyValue));
      });
    };

    this.setTrafficSigns = function(trafficSign, isShowing) {
      if(trafficSignsShowing[trafficSign] !== isShowing) {
        trafficSignsShowing[trafficSign] = isShowing;
        eventbus.trigger('trafficSigns:signsChanged', getTrafficSignsToShow());
      }
    };

    var getTrafficSignsToShow = function(){
      var signsToShow = [];
      _.forEach(trafficSignsShowing, function (isShowing, trafficSign) {
        if(isShowing)
          signsToShow = signsToShow.concat(trafficSignValues[trafficSign]);
      });
      return signsToShow;
    };

    this.fetch = function(boundingBox) {
      return backend.getPointAssetsWithComplementary(boundingBox, layerName)
        .then(function(assets) {
          eventbus.trigger('pointAssets:fetched');
          me.allowComplementaryIsActive(allowComplementary);
          return filterTrafficSigns(me.filterComplementaries(assets));
        });
    };
  };

})(this);