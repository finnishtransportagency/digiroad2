(function(root) {
  root.TrafficSignsCollection = function (backend, specs, verificationCollection) {
    PointAssetsCollection.call(this);
    var me = this;
    this.trafficSignsAsset = [];

    var trafficSignsShowing = {
      generalWarningSigns: true,
      priorityAndGiveWaySigns: true,
      prohibitionsAndRestrictions: true,
      mandatorySigns: true,
      regulatorySigns: true,
      informationSigns: true,
      serviceSigns: true,
      otherSigns: true,
      additionalPanels: false
      };

    var trafficSignValues = {
      generalWarningSigns: { values : [9, 36, 37, 38, 39, 40, 41, 42, 43, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 125, 126, 127, 128, 129, 130, 131, 132, 133, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213],
                            groupName: 'Varoitusmerkit', groupIndicative: 'A'},
      priorityAndGiveWaySigns: { values: [94, 95, 96, 97, 98, 99, 214], groupName: 'Etuajo-oikeus ja väistämismerkit', groupIndicative: 'B'},
      prohibitionsAndRestrictions: { values : [1, 2, 3, 4, 8, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 80, 81, 100, 101, 102, 103, 104, 134, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224],
                                      groupName: 'Kielto- ja rajoitusmerkit', groupIndicative: 'C' },
      mandatorySigns: { values: [70, 71, 72, 74, 77, 78, 135, 136, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238], groupName: 'Määräysmerkit', groupIndicative: 'D'},
      regulatorySigns: { values: [5, 6, 7, 63, 64, 65, 66, 68, 69, 105, 106, 107, 108, 109, 110, 111, 112, 137, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255, 256, 257, 258, 259, 260, 261, 262, 263],
                         groupName: 'Sääntömerkit', groupIndicative: 'E'},
      informationSigns: { values: [113, 114, 115, 116, 117, 118, 119, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 264, 265, 266, 267, 268, 269, 270, 271, 272, 273, 274, 275, 276, 277, 278, 279, 280, 281, 282, 283, 284, 285, 286, 287, 288, 289, 290, 291, 292, 293, 294, 295, 296, 297, 298, 299, 300, 301, 302, 303, 390, 391, 392, 393, 394, 395, 396, 397, 398, 399, 400],
                          groupName: 'Opastusmerkit', groupIndicative: 'F'},
      serviceSigns: { values: [120, 121, 122, 123, 124, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 314, 315, 316, 317, 318, 319, 320, 321, 322, 323, 324, 325, 326, 327, 328, 329, 330, 331, 332, 333, 334, 335, 336, 337, 338, 339, 340, 341, 342, 343, 344],
                      groupName: 'Palvelukohteet', groupIndicative: 'G'},
      otherSigns: { values: [371, 372, 373, 374, 375, 376, 377, 378, 379, 382, 383, 384, 385, 386, 387, 388, 389], groupName: 'Muut merkit', groupIndicative: 'I'}
    };

    var additionalValues = {
        additionalPanels: { values : [45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 361, 362, 363],
                            groupName: 'Lisakilvet', groupIndicative: 'H'}
    };

    var trafficSignsTypeLinearGenerators = [10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 85, 100, 101];

    var trafficSignsNoLongerAvailable = ["143", "147", "162", "166", "167", "168", "247", "274", "287", "288", "357", "359"];

    this.isNoLongerAvailable = function (currentSignValue) {
      return _.includes(trafficSignsNoLongerAvailable, currentSignValue);
    };

    var isTrafficSignTypeLinearGenerator = function(current) {
      return _.includes(trafficSignsTypeLinearGenerators, parseInt(getValue(current)));
    };

    this.signTypesAllowedInPedestrianCyclingLinks = ['70', '71', '72', '235', '236', '85', '89', '9', '98', '99', '14', '30',
      '31', '32', '111', '112', '163', '164', '280', '281', '282', '283', '284', '285', '398', '118', '119', '187', '298',
      '188', '299', '300', '301', '189', '302', '190', '303', '362', '61'];

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