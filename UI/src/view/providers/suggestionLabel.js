(function(root) {

  root.SuggestionLabel = function() {
    AssetLabel.call(this);
    var me = this;

    // this.MIN_DISTANCE = groupingDistance;
    //
    // me.getSignType = function (sign) {
    //   return sign.type;
    // };
    //
    // var getSignExtensionType = function (sign) {
    //   return sign.typeExtension;
    // };
    //
    // me.getPropertiesConfiguration = function () {
    //   return [
    //     {signValue: [15], image: 'images/service_points/parkingGarage.png'},
    //     {signValue: [12], image: 'images/service_points/parking.png'},
    //     {signValue: [16], image: 'images/service_points/busStation.png'},
    //     {signValue: [8], image: 'images/service_points/airport.png'},
    //     {signValue: [9], image: 'images/service_points/ferry.png'},
    //     {signValue: [10], image: 'images/service_points/taxiStation.png', height: 30},
    //     {signValue: [6], image: 'images/service_points/picnicSite.png'},
    //     {signValue: [4], image: 'images/service_points/customsControl.png'},
    //     {signValue: [5], image: 'images/service_points/borderCrossingPoint.png', validation: validateText, height: 25},
    //     {signValue: [13], image: 'images/service_points/loadingTerminalForCars.png', validation: validateText, height: 25},
    //     {signValue: [14], image: 'images/service_points/parkingAreaBusesAndTrucks.png', validation: validateText, height: 25},
    //     {signValue: [17], image: 'images/service_points/chargingPointElectricCars.png', validation: validateText, height: 25},
    //     {signValue: [11], typeExtension: 5, image: 'images/service_points/railwayStation2.png'},
    //     {signValue: [11], typeExtension: 6, image: 'images/service_points/railwayStation.png'},
    //     {signValue: [11], typeExtension: 7, image: 'images/service_points/subwayStation.png'},
    //     {signValue: [18], image: 'images/service_points/e18rekkaparkki.png', validation: validateText, height: 25}
    //   ];
    // };
    //
    // me.getLabel = function(sign){
    //  return _.find(me.getPropertiesConfiguration(), function(properties) {
    //     var includesSign = _.includes(properties.signValue, me.getSignType(sign));
    //     if(properties.typeExtension)
    //       return includesSign && properties.typeExtension === getSignExtensionType(sign);
    //     else
    //       return includesSign;
    //   });
    // };
    //
    // var validateText = function () { return true;};

    var getProperty = function (asset, publicId) {
      return _.head(_.find(asset.propertyData, function (prop) {
        return prop.publicId === publicId;
      }).values);
    };

    me.renderFeaturesByPointAssets = function(pointAssets, zoomLevel){
      return me.renderGroupedFeatures(pointAssets, zoomLevel, function(asset){
        return me.getCoordinate(asset);
      });
    };

    this.renderGroupedFeatures = function(assets, zoomLevel, getPoint){
      if(!this.isVisibleZoom(zoomLevel))
        return [];
      var groupedAssets = me.getGroupedFeatures(assets, zoomLevel);
      return _.flatten(_.chain(groupedAssets).map(function(assets){
        var imgPosition = {x: 0.5 , y: 61};
        return _.map(assets, function(asset){
          var styles = [];
          styles = me.suggestionStyle(getProperty(asset,"suggest_box"), imgPosition, styles);

          var feature = me.createFeature(getPoint(asset));
          feature.setStyle(styles);
          feature.setProperties(_.omit(asset, 'geometry'));
          return feature;
        });
      }).filter(function(feature){ return !_.isUndefined(feature); }).value());
    };
  };
})(this);
