(function(root) {

  root.TrafficSignsCollection = function (backend, specs, verificationCollection) {
    PointAssetsCollection.call(this);
    var me = this;
    this.trafficSignsAsset = [];

    var trafficSignsShowing = {
      speedLimits: false,
      regulatorySigns: false,
      maximumRestrictions: false,
      generalWarningSigns: false,
      prohibitionsAndRestrictions: false,
      additionalPanels: false,
      mandatorySigns: false,
      priorityAndGiveWaySigns: false,
      informationSigns: false,
      serviceSigns: false
    };

    var trafficSignValues = {
      speedLimits: { values : [1, 2, 3, 4, 5, 6], groupName: 'Nopeusrajoitukset' },
      regulatorySigns: {values: [7, 63, 64, 65, 66, 67, 68, 69, 105, 106, 107, 108, 109, 110, 111, 112, 137], groupName: 'Ohjemerkit'},
      maximumRestrictions: { values : [8, 30, 31, 32, 33, 34, 35], groupName: 'Suurin sallittu - rajoitukset'},
      generalWarningSigns: { values : [9, 36, 37, 38, 39, 40, 41, 42, 43, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 125, 126, 127, 128, 129, 130, 131, 132, 133], groupName: 'Varoitukset'},
      prohibitionsAndRestrictions: { values : [10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 80, 81, 100, 101, 102, 103, 104, 134], groupName: 'Kiellot ja rajoitukset'},
      mandatorySigns: {values: [70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 136, 135], groupName: 'Määraysmerkit'},
      priorityAndGiveWaySigns: {values: [94, 95, 96, 97, 98, 99], groupName: 'Etuajo-oikeus- ja väistämismerkit'},
      informationSigns: {values: [113, 114, 115, 116, 117, 118, 119, 178, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193], groupName: 'Opastusmerkit'},
      serviceSigns: {values: [120, 121, 122, 123, 124], groupName: 'Palvelukohteiden opastusmerkit'}
    };

    var additionalValues = {
        additionalPanels: { values : [45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 147, 146, 145, 144, 143, 142, 141, 140, 139, 138, 148, 149, 150, 151], groupName: 'Lisakilvet'}
    };

    var trafficSignsTurnRestriction = [10, 11, 12];
    var trafficSignRestriction = [13, 14, 15, 16, 17, 18, 19, 21, 22, 23, 24, 25, 26, 20, 100, 101];

    var isTurningRestriction = function(current) {
      return _.includes(trafficSignsTurnRestriction, parseInt(getValue(current)));
    };

    var isTrafficSignRestriction = function(current) {
      var propertyValue = _.head(_.find(current.propertyData, function(prop){return prop.publicId === "trafficSigns_type";}).values).propertyValue;
      return _.includes(trafficSignRestriction, parseInt(propertyValue));
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
        _.map(values, function(signType) {
          return _.find(_.map(signValues, function(trafficSignGroup, trafficSignGroupName){
            return {
              label: trafficSignGroup.groupName,
              types: trafficSignGroup.values,
              groupName: trafficSignGroupName,
              propertyValue: signType.propertyValue,
              propertyDisplayValue: signType.propertyDisplayValue };
            }), function(groups) {
            return _.some(groups.types, function(group){ return group == signType.propertyValue;  });
          });
        }), function(groups) {
          return groups.label;
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
      return backend.getPointAssetsWithComplementary(boundingBox, specs.layerName)
        .then(function(assets) {
          eventbus.trigger('pointAssets:fetched');
          me.allowComplementaryIsActive(specs.allowComplementaryLinks);
          verificationCollection.fetch(boundingBox, center, specs.typeId, specs.hasMunicipalityValidation);
          me.trafficSignsAsset = assets;
          return filterTrafficSigns(me.filterComplementaries(assets));
        });
    };

    var isRelevant = function(current) {

      if (isTurningRestriction(current) || isTrafficSignRestriction(current)) {
        var oldTrafficSign = _.find(me.trafficSignsAsset, function (oldAsset) { return oldAsset.id === current.id; });
        var oldTrafficSignTypeValue = _.head(_.find(oldTrafficSign.propertyData, function(property) { return property.publicId === "trafficSigns_type";}).values).propertyValue;

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
      if ((isTrafficSignRestriction(current) || isTurningRestriction(current)) && action !== 'updated') {
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