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

    const trafficSignValuesReadOnly = enumerations.trafficSignValuesReadOnly;

    this.getGroup = function(signTypes){
      return  _.groupBy(
        _.map(signTypes[0], function(signType) {
          return _.find(_.map(trafficSignValuesReadOnly, function(trafficSignGroup, trafficSignGroupName){
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
          signsToShow = signsToShow.concat(trafficSignValuesReadOnly[trafficSign].values);
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