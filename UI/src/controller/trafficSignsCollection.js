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
      mandatorySigns: false,
      priorityAndGiveWaySigns: false,
      informationSigns: false,
      serviceSigns: false
    };

    var trafficSignValues = {
      speedLimits: { values : [361, 362, 363, 364, 571, 572], groupName: 'Nopeusrajoitukset' },
      regulatorySigns: {values: [511, 520, 541, 542, 543, 531, 533, 534, 521, 551, 561, 562, 573, 574, 575, 576, 532], groupName: 'Ohjemerkit'},
      maximumRestrictions: { values : [343, 341, 342, 344, 345, 346, 347], groupName: 'Suurin sallittu - rajoitukset'},
      generalWarningSigns: { values : [189, 111, 112, 113, 114, 115, 116, 141, 152, 121, 122, 131, 142, 144, 151, 153, 161, 162, 163, 164, 165, 167, 181, 183, 171, 172, 176, 177, 155, 156], groupName: 'Varoitukset'},
      prohibitionsAndRestrictions: { values : [332, 333, 334, 311, 312, 313, 314, 315, 316, 317, 318, 319, 321, 322, 323, 324, 325, 331, 351, 352, 375, 376, 371, 372, 373, 374, 381, 382], groupName: 'Kiellot ja rajoitukset'},
      mandatorySigns: {values: [421, 422, 423, 424, 425, 413, 414, 415, 416, 417, 418, 426, 427], groupName: 'Maaraysmerkit'},
      priorityAndGiveWaySigns: {values: [211, 212, 221, 222, 231, 232], groupName: 'Etuajo-oikeus- ja vaistamismerkit'},
      informationSigns: {values: [651, 652, 671, 677, 681, 682, 683], groupName: 'Opastusmerkit'},
      serviceSigns: {values: [704, 715, 722, 724, 726], groupName: 'Palvelukohteiden opastusmerkit'}
    };

    var additionalValues = {
        additionalPanels: { values : [821, 822, 848, 849, 851, 852, 854, 831, 832, 833, 834, 836, 841, 843, 855, 856, 871, 872, 816, 823, 824, 825, 826, 827, 828, 853, 861, 862], groupName: 'Lisakilvet'}
    };

    var trafficSignsTurnRestriction = [10, 11, 12];

    var isTurningRestriction = function(current) {
      return _.includes(trafficSignsTurnRestriction, parseInt(getValue(current)));
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

    var isRelevantToManoeuvres = function(current) {

      if (isTurningRestriction(current)) {
        var oldTrafficSign = _.find(me.trafficSignsAsset, function (oldAsset) { return oldAsset.id === current.id; });
        var oldTrafficSignTypeValue = _.head(_.find(oldTrafficSign.propertyData, function(property) { return property.publicId === "trafficSigns_type";}).values).propertyValue;

        //if traffic type changes, should be relevant to Manoeuvres
        if (oldTrafficSignTypeValue !== getValue(current))
          return true;

        var diffProperties = _.reduce(oldTrafficSign, function (result, value, key) {
          return _.isEqual(value, current[key]) ?
            result : result.concat(key);
        }, []);

        //if traffic Signs moves or orientation changes, should be relevant to Manoeuvres
       return _.some(diffProperties, function(prop) {
            return _.includes(['validityDirection', 'lon', 'lat'], prop);
        });
      } else
        return false;
    };

    eventbus.on('trafficSigns:updated', function(current) {
      if(isRelevantToManoeuvres(current)) {
        new GenericConfirmPopup('Huom! Liikennemerkin siirto saattaa vaikuttaa myös olemassa olevan kääntymisrajoituksen sijaintiin.',
          {type: 'alert'});
      }
    });
    eventbus.on('trafficSigns:deleted', function(current) {
      if(isTurningRestriction(current)) {
        new GenericConfirmPopup('Huom! Liikennemerkin poisto saattaa vaikuttaa myös olemassa olevan kääntymisrajoituksen sijaintiin.',
          {type: 'alert'});
      }
    });
  };

})(this);