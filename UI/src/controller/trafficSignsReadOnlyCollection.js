(function(root) {
  root.TrafficSignsReadOnlyCollection = function (backend, layerName, allowComplementary) {
    PointAssetsCollection.call(this);
    var me = this;

      const enumerations = new Enumerations();

      var trafficSignsShowing = {
      speedLimit: false,
      totalWeightLimit: false,
      trailerTruckWeightLimit: false,
      axleWeightLimit: false,
      bogieWeightLimit: false,
      heightLimit: false,
      lengthLimit: false,
      widthLimit: false,
      prohibition: false,
      hazardousMaterialTransportProhibition: false,
      manoeuvre: false ,
      pedestrianCrossings: false,
      parking: false,
      trafficSigns: false
    };

    var trafficSignValues = {
      speedLimit: { values : [1, 2, 3, 4, 5, 6]},
      totalWeightLimit: {values : [32, 33, 34, 35]},
      trailerTruckWeightLimit: {values : [32, 33, 34, 35]},
      axleWeightLimit:{values : [32, 33, 34, 35]},
      bogieWeightLimit: {values : [32, 33, 34, 35]},
      heightLimit: {values : [31]},
      lengthLimit: {values : [8]},
      widthLimit: {values : [30]},
      prohibition: {values: [13, 14, 15, 16, 17, 18, 19, 21, 22, 23, 24, 25, 26]},
      parkingProhibition: {values: [100, 101]},
      hazardousMaterialTransportProhibition: {values : [20]},
      manoeuvre: {values: [10, 11, 12]},
      pedestrianCrossings: { values: [7] },
      trafficSigns: {values: [45,46,139,140,141,142,143,144,47,48,49,50,145,51,138,146,147,52,53,54,55,56,57,58,59,60,61,62,148,149,150,151]}, //remove after batch to merge additional panels (1707) is completed. part of experimental feature
      cyclingAndWalking: {values: enumerations.trafficSignsAllowedOnPedestrianCyclingLinks},
      roadWork: { values: [85] } //layername
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
        var existingValue = _.head(_.find(asset.propertyData, function(prop){return prop.publicId === "trafficSigns_type";}).values);
        if(!existingValue)
          return false;
        return _.includes(getTrafficSignsToShow(), parseInt(existingValue.propertyValue));
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
      return backend.getTrafficSignsWithComplementary(boundingBox)
        .then(function(assets) {
          eventbus.trigger('pointAssets:fetched');
          me.allowComplementaryIsActive(allowComplementary);
          return filterTrafficSigns(me.filterComplementaries(assets));
        });
    };
  };

})(this);