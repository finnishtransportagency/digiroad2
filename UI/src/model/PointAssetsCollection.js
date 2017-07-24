(function(root) {
  root.PointAssetsCollection = function(backend, endPointName) {
    var isComplementaryActive = false;
    var isTrafficSigns = (endPointName == 'trafficSigns');
    var trafficSignsShowing = {
      speedLimits: false, //[1, 2, 3, 4, 5, 6]
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


    var filterComplementaries = function (assets) {
      if(isComplementaryActive)
        return assets;
      return _.where(assets, {linkSource: 1});
    };

     var filterTrafficSigns = function (asset) {
       return _.filter(asset, function (asset) {
         var existingValue = _.first(_.find(asset.propertyData, function(prop){return prop.publicId === "liikennemerkki_tyyppi";}).values);
         if(!existingValue)
           return false;
         return _.contains(getTrafficSignsToShow(), parseInt(existingValue.propertyValue));
       });
     };

    var setTrafficSigns = function(trafficSign, isShowing) {
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

    function fetch(boundingBox) {
      return backend.getPointAssetsWithComplementary(boundingBox, endPointName)
        .then(function(assets) {
          eventbus.trigger('pointAssets:fetched');
          if(isTrafficSigns)
            return filterTrafficSigns(filterComplementaries(assets));
          return filterComplementaries(assets);
        });
    }

    function activeComplementary(enable) {
      isComplementaryActive = enable;
    }

    function complementaryIsActive() {
      return isComplementaryActive;
    }

    return {
      fetch: fetch,
      activeComplementary : activeComplementary,
      complementaryIsActive : complementaryIsActive,
      setTrafficSigns : setTrafficSigns
    };
  };
})(this);