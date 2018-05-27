(function(root) {

  root.TrafficSignsReadOnlyCollection = function (backend, layerName, allowComplementary) {
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
      lengthLimit: false,
      widthLimit: false
    };

    var trafficSignValues = {
      speedLimit: { values : [1, 2, 3, 4, 5, 6]},
      trSpeedLimits: { values : [1, 2, 3, 4, 5, 6]},
      totalWeightLimit: {values : [32]},
      trailerTruckWeightLimit: {values : [33]},
      axleWeightLimit:{values : [34]},
      bogieWeightLimit: {values : [35]},
      heightLimit: {values : [31]},
      lengthLimit: {values : [8]},
      widthLimit: {values : [30]}
    };

    this.getGroup = function(signTypes){
      return  _.groupBy(
        _.map(signTypes[0], function(signType) {
          return _.find(_.map(trafficSignValues, function(trafficSignGroup, trafficSignGroupName){
            return {
              label: trafficSignGroup.groupName,
              types: trafficSignGroup.values,
              groupName: trafficSignGroupName,
              propertyValue: signType.propertyValue,
              propertyDisplayValue: signType.propertyDisplayValue };
          }), function(groups) {
            return _.some(groups.types, function(group){ return group == signType.propertyValue;  }); }); }), function(groups) {
          return groups.label;  });
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
          signsToShow = signsToShow.concat(trafficSignValues[trafficSign].values);
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