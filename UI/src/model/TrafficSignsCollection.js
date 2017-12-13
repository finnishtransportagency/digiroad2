(function(root) {

  root.TrafficSignsCollection = function (backend, layerName, allowComplementary) {
    PointAssetsCollection.call(this);
    var me = this;

    var trafficSignsShowing = {
      speedLimits: false,
      pedestrianCrossings: false,
      maximumRestrictions: false,
      generalWarningSigns: false,
      prohibitionsAndRestrictions: false
    };

    var trafficSignValues = {
      speedLimits: { values : [1, 2, 3, 4, 5, 6], groupName: 'Nopeusrajoitukset' },
      pedestrianCrossings: { values : [7], groupName: 'Suojatiet'},
      maximumRestrictions: { values : [8, 30, 31, 32, 33, 34, 35], groupName: 'Suurin sallittu - rajoitukset'},
      generalWarningSigns: { values : [9, 36, 37, 38, 39, 40, 41, 42, 43], groupName: 'Varoitukset'},
      prohibitionsAndRestrictions: { values : [10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29], groupName: 'Kiellot ja rajoitukset'}
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