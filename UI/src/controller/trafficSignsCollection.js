(function(root) {
  root.TrafficSignsCollection = function (backend, specs, verificationCollection) {
    PointAssetsCollection.call(this);
    var me = this;
    this.trafficSignsAsset = [];

    var enumerations = new Enumerations();

    var trafficSignsShowing = {
      generalWarningSigns: true,
      priorityAndGiveWaySigns: true,
      prohibitionsAndRestrictions: true,
      mandatorySigns: true,
      regulatorySigns: true,
      informationSigns: true,
      serviceSigns: true,
      otherSigns: true,
      additionalPanels: false,
      textualSign: true
      };

    var trafficSignValues = enumerations.trafficSignValues;

    var additionalValues = enumerations.additionalValues;

    var trafficSignsTypeLinearGenerators = enumerations.trafficSignsTypeLinearGenerators;

    var trafficSignsNoLongerAvailable = enumerations.trafficSignsNoLongerAvailable.map(function(num) {
      return num.toString();
    });

    this.isNoLongerAvailable = function (currentSignValue) {
      return _.includes(trafficSignsNoLongerAvailable, currentSignValue);
    };

    var isTrafficSignTypeLinearGenerator = function(current) {
      return _.includes(trafficSignsTypeLinearGenerators, parseInt(getValue(current)));
    };

    this.signTypesAllowedInPedestrianCyclingLinks = enumerations.trafficSignsAllowedOnPedestrianCyclingLinks.map(function(num) {
      return num.toString();
    });

    this.additionalPanelsAllowedInPedestrianCyclingLinks = enumerations.additionalPanelsAllowedOnPedestrianCyclingLinks.map(function(num) {
      return num.toString();
    });

    this.isAllowedSignInPedestrianCyclingLinks = function (signType) {
      return _.includes(this.signTypesAllowedInPedestrianCyclingLinks, signType);
    };

    var getValue = function(current) {
      return _.head(_.find(current.propertyData, function(property) { return property.publicId === "trafficSigns_type";}).values).propertyValue;
    };

    this.getGroup = function(signTypes){
      var mainSignTypes = _.filter(_.head(signTypes), function(x){ return !_.includes(additionalValues.additionalPanels.values, parseInt(x.propertyValue));});
      return propertyHandler(mainSignTypes, trafficSignValues);
    };

    this.getAdditionalPanels = function(signTypes){
        var additionalPanels = _.filter(_.head(signTypes), function(x){ return _.includes(additionalValues.additionalPanels.values, parseInt(x.propertyValue));});
        return propertyHandler(additionalPanels, additionalValues);
    };

    var propertyHandler = function (values, signValues) {
      return  _.groupBy(
        _.sortBy(_.map(values, function(signType) {
          return _.find(_.map(signValues, function(trafficSignGroup, trafficSignGroupName){
            return {
              label: trafficSignGroup.groupName,
              types: trafficSignGroup.values,
              indicative: trafficSignGroup.groupIndicative,
              groupName: trafficSignGroupName,
              propertyValue: signType.propertyValue,
              propertyDisplayValue: signType.propertyDisplayValue };
            }), function(groups) {
            return _.some(groups.types, function(group){ return group == signType.propertyValue;  });
          });
        }), ['indicative']), function(group) {
          return group.label;
        });
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

    this.fetch = function(boundingBox, center) {
      return backend.getTrafficSignsWithComplementary(boundingBox)
        .then(function(assets) {
          eventbus.trigger('pointAssets:fetched');
          me.allowComplementaryIsActive(specs.allowComplementaryLinks);
          verificationCollection.fetch(boundingBox, center, specs.typeId, specs.hasMunicipalityValidation);
          me.trafficSignsAsset = assets;
          return filterTrafficSigns(me.filterComplementaries(assets));
        });
    };

    var isRelevant = function(current) {
      if (isTrafficSignTypeLinearGenerator(current)) {
        var oldTrafficSign = _.find(me.trafficSignsAsset, function (oldAsset) { return oldAsset.id === current.id; });
        var oldTrafficSignTypeValue = getValue(oldTrafficSign);

        //if traffic type changes, should be relevant to Manoeuvres
        if (oldTrafficSignTypeValue !== getValue(current))
          return true;

        var diffProperties = _.reduce(oldTrafficSign, function (result, value, key) {
          return _.isEqual(value, current[key]) ?
            result : result.concat(key);
        }, []);
        
       return _.some(diffProperties, function(prop) {
            return _.includes(['validityDirection', 'lon', 'lat', 'propertyData'], prop);
        });
      } else
        return false;
    };

    var getTrafficSignsMessage = function (current, action) {
      if (isTrafficSignTypeLinearGenerator(current) && action !== 'updated') {
        return trafficSignsActionMessages[action].message;
      } else if (isRelevant(current)) {
        return trafficSignsActionMessages[action].message;
      }
    };

    var trafficSignsActionMessages = {
      created: {message: 'Tähän liikennemerkkiin liittyvä kohde luodaan seuraavan yön aikana.'},
      updated: {message: 'Huom! Liikennemerkin päivittäminen saattaa vaikuttaa myös siihen liittyvään kohteeseen.'},
      deleted: {message: 'Huom! Liikennemerkin poistaminen saattaa vaikuttaa myös siihen liittyvään kohteeseen.'}
    };


    eventbus.on('trafficSigns:created trafficSigns:updated trafficSigns:deleted', function (current, action) {
      var message = getTrafficSignsMessage(current, action);
      if (!_.isUndefined(message)) {
        new GenericConfirmPopup(message,
          {type: 'alert'});
      }
    });
  };

})(this);