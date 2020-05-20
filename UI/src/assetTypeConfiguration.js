(function(root) {
  root.AssetTypeConfiguration = function () {

    var oneKmZoomLvl = 8;

    var assetType = {
      massTransitStop: 10,
      speedLimit: 20,
      totalWeightLimit: 30,
      trailerTruckWeightLimit: 40,
      axleWeightLimit: 50,
      bogieWeightLimit: 60,
      heightLimit: 70,
      lengthLimit: 80,
      widthLimit: 90,
      litRoad: 100,
      pavedRoad: 110,
      roadWidth: 120,
      roadDamagedByThaw: 130,
      numberOfLanes: 140,
      massTransitLane: 160,
      trafficVolume: 170,
      winterSpeedLimit: 180,
      prohibition: 190,
      pedestrianCrossings: 200,
      hazardousMaterialTransportProhibition: 210,
      obstacles: 220,
      railwayCrossings: 230,
      directionalTrafficSigns: 240,
      servicePoints: 250,
      europeanRoads: 260,
      exitNumbers: 270,
      trafficLights: 280,
      maintenanceRoad: 290,
      trafficSigns: 300,
      trSpeedLimits: 310,
      trWeightLimits: 320,
      trTrailerTruckWeightLimits: 330,
      trAxleWeightLimits: 340,
      trBogieWeightLimits: 350,
      trHeightLimits: 360,
      trWidthLimits: 370,
      manoeuvre: 380,
      careClass: 390,
      carryingCapacity: 400,
      roadWorksAsset: 420,
      parkingProhibition: 430,
      cyclingAndWalking: 440,
      laneModellingTool: 450
    };

    var assetGroups = {
      trWeightGroup: [assetType.trWeightLimits, assetType.trTrailerTruckWeightLimits, assetType.trAxleWeightLimits, assetType.trBogieWeightLimits]
    };

    var dateValueExtract = function (fields, publicId) {
      var dateValue = _.find(fields, function(field) { return field.publicId === publicId; }).values;
      return !_.isEmpty(dateValue) ? new Date(_.head(dateValue).propertyValue.replace(/(\d+).(\d+).(\d{4})/, "$2/$1/$3")) : undefined;
    };

    var saveConditionWithSuggested = function(selectedAsset, authorizationPolicy) {
      var selected = selectedAsset.get();
      return !(selected.isSuggested && authorizationPolicy.isMunicipalityMaintainer()) || authorizationPolicy.isOperator();
    };

    var dateExtract = function (value) {
      return new Date(value.replace(/(\d+).(\d+).(\d{4})/, "$2/$1/$3"));
    };

    var datePeriodValueExtract = function (date) {
      var datePeriodValue = date.getPropertyValue().values;
      var startDate = dateExtract(_.head(datePeriodValue).value.startDate);
      var endDate = dateExtract(_.head(datePeriodValue).value.endDate);

      return {startDate: startDate, endDate: endDate};
    };

    var isValidPeriodDate = function (startDate, endDate) {
      return startDate <= endDate;
    };

    var isEndDateAfterStartdate = function (date) {
      var datePeriods = datePeriodValueExtract(date);
      return isValidPeriodDate(datePeriods.startDate, datePeriods.endDate);
    };

    var showSuggestBox = function (authorizationPolicy, selectedLinearAsset, value, layerMode) {
      return authorizationPolicy.handleSuggestedAsset(selectedLinearAsset, value, layerMode);
    };

    var isSuggestBoxUnset = function (selectedLinearAsset) {
      return _.some(selectedLinearAsset.get(), function (asset) {return asset.id;});
    };

    var numericValidation = function (fields) {
      var numericalFields = _.filter(fields, function(field) {return field.propertyType === 'number';});
      return _.every(numericalFields, function(field) {return _.isEmpty(field.values) || !isNaN(_.head(field.values).propertyValue);});
    };

    var lanesValidation = function (fields, laneNumberProperty, laneTypeProperty) {
      var laneValues = laneNumberProperty.values;
      var isValidLaneValue = _.isEmpty(laneValues) || _.isEmpty(_.head(laneValues).propertyValue) || /^([1-3][1-9])$/.test(_.head(laneValues).propertyValue);

      var laneTypeValue = laneTypeProperty.values;

      return isValidLaneValue && (_.isEmpty(laneValues) || _.head(laneTypeValue).propertyValue == 999 ||
          (_.head(laneValues).propertyValue.charAt(1) != 1 && _.head(laneTypeValue).propertyValue != 1) ||
          (_.head(laneValues).propertyValue.charAt(1) == 1 && _.head(laneTypeValue).propertyValue == 1));
    };

    var cyclingAndWalkingValidator = function(selectedLinearAsset, id) {
      if (_.isUndefined(selectedLinearAsset) || _.isUndefined(id))
        return false;

      var currentAsset = _.head(selectedLinearAsset);

      return function() {
        switch (id) {
          case 3: return currentAsset.functionalClass !== 8 || currentAsset.linkType !== 8;
          case 4: return ![1,3].includes(currentAsset.administrativeClass);
          case 5: return currentAsset.administrativeClass !== 2;
          case 18: return currentAsset.linkType !== 12;
          default: return false;
        }
      };
    };

    //To use the new lane preview on the assets form(point and dynamic forms) put lanePreview: true

    var linearAssetSpecs = [
      {
        typeId: assetType.totalWeightLimit,
        singleElementEventCategory: 'totalWeightLimit',
        multiElementEventCategory: 'totalWeightLimits',
        layerName: 'totalWeightLimit',
        title: 'Suurin sallittu massa',
        newTitle: 'Uusi suurin sallittu massa',
        className: 'total-weight-limit',
        unit: 'kg',
        isSeparable: false,
        allowComplementaryLinks: true,
        editControlLabels: {
          title: 'Rajoitus',
          enabled: 'Rajoitus',
          disabled: 'Ei rajoitusta',
          additionalInfo : 'Muut massarajoitukset',
          showUnit: true
        },
        label: new MassLimitationsLabel(),
        readOnlyLayer: TrafficSignReadOnlyLayer,
        isVerifiable: true,
        hasInaccurate: true,
        hasMunicipalityValidation: true,
        authorizationPolicy: new LinearStateRoadAuthorizationPolicy(),
        isMultipleLinkSelectionAllowed: true,
        minZoomForContent: oneKmZoomLvl,
        form: new DynamicAssetForm({
          fields: [
            {label: "massarajoitus", type: 'integer', publicId: "weight", unit: "Kg", required: true, weight: 1},
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", defaultValue: "0", values: [{id: 0, label: 'Tarkistettu'}, {id: 1, label: 'Vihjetieto'}], weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
          ]
        })
      },
      {
        typeId: assetType.trailerTruckWeightLimit,
        singleElementEventCategory: 'trailerTruckWeightLimit',
        multiElementEventCategory: 'trailerTruckWeightLimits',
        layerName: 'trailerTruckWeightLimit',
        title: 'Yhdistelmän suurin sallittu massa',
        newTitle: 'Uusi yhdistelmän suurin sallittu massa',
        className: 'trailer-truck-weight-limit',
        unit: 'kg',
        isSeparable: false,
        allowComplementaryLinks: true,
        editControlLabels: { title: 'Rajoitus',
          enabled: 'Rajoitus',
          disabled: 'Ei rajoitusta',
          additionalInfo : 'Muut massarajoitukset',
          showUnit: true
        },
        label: new MassLimitationsLabel(),
        readOnlyLayer: TrafficSignReadOnlyLayer,
        isVerifiable: true,
        hasInaccurate: true,
        hasMunicipalityValidation: true,
        authorizationPolicy: new LinearStateRoadAuthorizationPolicy(),
        isMultipleLinkSelectionAllowed: true,
        minZoomForContent: oneKmZoomLvl,
        form: new DynamicAssetForm({
          fields: [
            {label: "massarajoitus", type: 'integer', publicId: "weight", unit: "Kg", required: true, weight: 1},
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", defaultValue: "0", values: [{id: 0, label: 'Tarkistettu'}, {id: 1, label: 'Vihjetieto'}], weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
          ]
        })
      },
      {
        typeId: assetType.axleWeightLimit,
        singleElementEventCategory: 'axleWeightLimit',
        multiElementEventCategory: 'axleWeightLimits',
        layerName: 'axleWeightLimit',
        title: 'Suurin sallittu akselimassa',
        newTitle: 'Uusi suurin sallittu akselimassa',
        className: 'axle-weight-limit',
        unit: 'kg',
        isSeparable: false,
        allowComplementaryLinks: true,
        editControlLabels: { title: 'Rajoitus',
          enabled: 'Rajoitus',
          disabled: 'Ei rajoitusta',
          additionalInfo : 'Muut massarajoitukset',
          showUnit: true
        },
        label: new MassLimitationsLabel(),
        readOnlyLayer: TrafficSignReadOnlyLayer,
        isVerifiable: true,
        hasInaccurate: true,
        hasMunicipalityValidation: true,
        authorizationPolicy: new LinearStateRoadAuthorizationPolicy(),
        isMultipleLinkSelectionAllowed: true,
        minZoomForContent: oneKmZoomLvl,
        form: new DynamicAssetForm({
          fields: [
            {label: "massarajoitus", type: 'integer', publicId: "weight", unit: "Kg", required: true, weight: 1},
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", defaultValue: "0", values: [{id: 0, label: 'Tarkistettu'}, {id: 1, label: 'Vihjetieto'}], weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
          ]
        })
      },
      {
        typeId: assetType.bogieWeightLimit,
        singleElementEventCategory: 'bogieWeightLimit',
        multiElementEventCategory: 'bogieWeightlLimits',
        layerName: 'bogieWeightLimit',
        title: 'Suurin sallittu telimassa',
        newTitle: 'Uusi suurin sallittu telimassa',
        className: 'bogie-weight-limit',
        unit: 'Kg',
        isSeparable: false,
        allowComplementaryLinks: true,
        editControlLabels: { title: 'Rajoitus',
          enabled: 'Rajoitus',
          additionalInfo : 'Muut massarajoitukset',
          disabled: 'Ei rajoitusta',
          showUnit: true
        },
        label: new MassLimitationsLabel(),
        readOnlyLayer: TrafficSignReadOnlyLayer,
        isVerifiable: true,
        hasInaccurate: true,
        hasMunicipalityValidation: true,
        authorizationPolicy: new LinearStateRoadAuthorizationPolicy(),
        isMultipleLinkSelectionAllowed: true,
        minZoomForContent: oneKmZoomLvl,
        form: new DynamicAssetForm({
        fields: [
            {label: "2-akselisen telin rajoitus", type: 'integer', publicId: "bogie_weight_2_axel", unit: "Kg", weight: 1},
            {label: "3-akselisen telin rajoitus", type: 'integer', publicId: "bogie_weight_3_axel", unit: "Kg", weight: 2},
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", defaultValue: "0", values: [{id: 0, label: 'Tarkistettu'}, {id: 1, label: 'Vihjetieto'}], weight: 3, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
          ]
        })
      },
      {
        typeId: assetType.heightLimit,
        singleElementEventCategory: 'heightLimit',
        multiElementEventCategory: 'heightLimits',
        layerName: 'heightLimit',
        title: 'Suurin sallittu korkeus',
        newTitle: 'Uusi suurin sallittu korkeus',
        className: 'height-limit',
        unit: 'cm',
        isSeparable: false,
        allowComplementaryLinks: true,
        editControlLabels: { title: 'Rajoitus',
          enabled: 'Rajoitus',
          disabled: 'Ei rajoitusta',
          showUnit: true
        },
        label: new DynamicAssetLabel(),
        readOnlyLayer: TrafficSignReadOnlyLayer,
        isVerifiable: true,
        hasInaccurate: true,
        hasMunicipalityValidation: true,
        isMultipleLinkSelectionAllowed: true,
        authorizationPolicy: new LinearStateRoadAuthorizationPolicy(),
        minZoomForContent: oneKmZoomLvl,
        form: new DynamicAssetForm({
          fields: [
            {label: "korkeusrajoitus", type: 'integer', publicId: "height", unit: "cm", required: true, weight: 1},
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", defaultValue: "0", values: [{id: 0, label: 'Tarkistettu'}, {id: 1, label: 'Vihjetieto'}], weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
          ]
        })
      },
      {
        typeId: assetType.lengthLimit,
        singleElementEventCategory: 'lengthLimit',
        multiElementEventCategory: 'lengthLimits',
        layerName: 'lengthLimit',
        title: 'Suurin sallittu pituus',
        newTitle: 'Uusi pituusrajoitus',
        className: 'length-limit',
        unit: 'cm',
        isSeparable: false,
        allowComplementaryLinks: true,
        editControlLabels: { title: 'Rajoitus',
          enabled: 'Rajoitus',
          disabled: 'Ei rajoitusta',
          showUnit: true
        },
        label: new DynamicAssetLabel(),
        readOnlyLayer: TrafficSignReadOnlyLayer,
        isVerifiable: true,
        hasInaccurate: true,
        hasMunicipalityValidation: true,
        isMultipleLinkSelectionAllowed: true,
        authorizationPolicy: new LinearAssetAuthorizationPolicyWithSuggestion(),
        minZoomForContent: oneKmZoomLvl,
        form: new DynamicAssetForm({
          fields: [
            {label: "pituusrajoitus", type: 'integer', publicId: "length", unit: "cm", required: true, weight: 1},
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", values: [{id: 0, label: 'Tarkistettu'}, {id: 1, label: 'Vihjetieto'}], weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
          ]
        })
      },
      {
        typeId: assetType.widthLimit,
        singleElementEventCategory: 'widthLimit',
        multiElementEventCategory: 'widthLimits',
        layerName: 'widthLimit',
        title: 'Suurin sallittu leveys',
        newTitle: 'Uusi suurin sallittu leveys',
        className: 'width-limit',
        unit: 'cm',
        isSeparable: false,
        allowComplementaryLinks: true,
        editControlLabels: {
          title: 'Rajoitus',
          enabled: 'Rajoitus',
          disabled: 'Ei rajoitusta',
          showUnit: true
        },
        label: new DynamicAssetLabel(),
        readOnlyLayer: TrafficSignReadOnlyLayer,
        isVerifiable: true,
        hasInaccurate: true,
        hasMunicipalityValidation: true,
        isMultipleLinkSelectionAllowed: true,
        authorizationPolicy: new LinearAssetAuthorizationPolicyWithSuggestion(),
        minZoomForContent: oneKmZoomLvl,
        form: new DynamicAssetForm({
          fields: [
            {label: "leveysrajoitus", type: 'integer', publicId: "width", unit: "cm", required: true, weight: 1},
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", defaultValue: "0", values: [{id: 0, label: 'Tarkistettu'}, {id: 1, label: 'Vihjetieto'}], weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
          ]
        })
      },
      {
        typeId: assetType.litRoad,
        defaultValue: 1,
        singleElementEventCategory: 'litRoad',
        multiElementEventCategory: 'litRoads',
        layerName: 'litRoad',
        title: 'Valaistus',
        newTitle: 'Uusi valaistus',
        className: 'lit-road',
        isSeparable: false,
        allowComplementaryLinks: true,
        editControlLabels: {
          title: 'Valaistus',
          enabled: 'Valaistus',
          disabled: 'Ei valaistusta'
        },
        authorizationPolicy: new LinearStateRoadAuthorizationPolicy(),
        isVerifiable: true,
        hasMunicipalityValidation: true,
        isMultipleLinkSelectionAllowed: true,
        minZoomForContent: oneKmZoomLvl,
        label: new LinearAssetWithSuggestLayer(),
        form: new DynamicAssetForm({
          fields: [
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", defaultValue: "0", values: [{id: 0, label: 'Tarkistettu'}, {id: 1, label: 'Vihjetieto'}], weight: 1, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
          ]
        })
      },
      {
        typeId: assetType.roadDamagedByThaw,
        defaultValue: 1,
        singleElementEventCategory: 'roadDamagedByThaw',
        multiElementEventCategory: 'roadsDamagedByThaw',
        layerName: 'roadDamagedByThaw',
        title: 'Kelirikko',
        newTitle: 'Uusi kelirikko',
        className: 'road-damaged-by-thaw',
        isSeparable: false,
        allowComplementaryLinks: true,
        editControlLabels: {
          title: 'Kelirikko',
          enabled: 'Kelirikko',
          disabled: 'Ei kelirikkoa',
          additionalInfo: 'Kelirikolle altis tie'
        },
        authorizationPolicy: new LinearStateRoadAuthorizationPolicy(),
        isVerifiable: false,
        label: new RoadDamagedByThawLabel(),
        style: new RoadDamagedByThawStyle(),
        saveCondition: function (fields) {
          var datePeriodField = _.filter(fields, function(field) { return field.getPropertyValue().propertyType === 'date_period'; });

          var isInDatePeriod = function(date) {
            var datePeriods = datePeriodValueExtract(date);
            return new Date(datePeriods.endDate.getMonth() + '/' + datePeriods.endDate.getDate() + '/' + (datePeriods.endDate.getFullYear() - 1)) <= datePeriods.startDate;
          };

          var isValidIntervalDate = _.every(datePeriodField, function (date) {
            return date.hasValue() ? isEndDateAfterStartdate(date) : true;
          });

          var datesAreValid =  _.every(datePeriodField, function(date) {
            return date.hasValue() && isInDatePeriod(date) && isEndDateAfterStartdate(date);
          });

          var isAnnualRepetition = _.some(_.filter(fields, function(field) {return field.getPropertyValue().publicId === 'annual_repetition';}), function(checkBox) { return checkBox.getValue(); });
          return isAnnualRepetition ? datesAreValid : isValidIntervalDate;
        },
        form: new DynamicAssetForm ( {
          fields : [
            { publicId: 'kelirikko', label: 'rajoitus', type: 'number', weight: 1, unit: 'kg'},
            { publicId: 'spring_thaw_period', label: 'Kelirikkokausi', type: 'date_period', multiElement: true, weight: 2},
            { publicId: "annual_repetition", label: 'Vuosittain toistuva', type: 'checkbox', values: [{id: 0, label: 'Ei toistu'}, {id: 1, label: 'Jokavuotinen'}], defaultValue: 0, weight: 3},
            { publicId: "suggest_box", label: "vihjetieto", type: 'checkbox', defaultValue: "0", values: [{id: 0, label: 'Tarkistettu'}, {id: 1, label: 'Vihjetieto'}],  weight: 4, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
          ]
        }),
        isMultipleLinkSelectionAllowed: true,
        hasMunicipalityValidation: true
      },
      {
        typeId: assetType.roadWidth,
        singleElementEventCategory: 'roadWidth',
        multiElementEventCategory: 'roadWidth',
        layerName: 'roadWidth',
        title: 'Leveys',
        newTitle: 'Uusi leveys',
        className: 'road-width',
        unit: 'cm',
        isSeparable: false,
        allowComplementaryLinks: true,
        editControlLabels: {
          title: 'Leveys',
          enabled: 'Leveys tiedossa',
          disabled: 'Leveys ei tiedossa',
          showUnit: true
        },
        label: new DynamicAssetLabel(),
        authorizationPolicy: new LinearStateRoadAuthorizationPolicy(),
        isVerifiable: true,
        hasMunicipalityValidation: true,
        isMultipleLinkSelectionAllowed: true,
        minZoomForContent: oneKmZoomLvl,
        form: new DynamicAssetForm({
          fields: [
            {label: "leveys", type: 'integer', publicId: "width", unit: "cm", required: true, weight: 1},
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", defaultValue: "0", values: [{id: 0, label: 'Tarkistettu'}, {id: 1, label: 'Vihjetieto'}], weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
          ]
        })
      },
      {
        typeId: assetType.pavedRoad,
        singleElementEventCategory: 'pavedRoad',
        multiElementEventCategory: 'pavedRoads',
        layerName: 'pavedRoad',
        title: 'Päällyste',
        newTitle: 'Uusi päällyste',
        className: 'paved-road',
        isSeparable: false,
        allowComplementaryLinks: true,
        editControlLabels: {
          title: 'Päällyste',
          enabled: 'Päällyste',
          disabled: 'Ei päällystettä'
        },
        authorizationPolicy: new LinearStateRoadAuthorizationPolicy(),
        isVerifiable: false,
        style: new PavedRoadStyle(),
        label: new LinearAssetWithSuggestLayer(),
        form: new DynamicAssetForm({
            fields : [
              {
                label: 'Paallysteluokka', type: 'single_choice', publicId: "paallysteluokka", defaultValue: "99", weight: 1,
                values: [
                  {id: 99, label: 'Päällystetty, tyyppi tuntematon'},
                  {id: 1, label: 'Betoni'},
                  {id: 2, label: 'Kivi'},
                  {id: 10, label: 'Kovat asfalttibetonit'},
                  {id: 20, label: 'Pehmeät asfalttibetonit'},
                  {id: 30, label: 'Soratien pintaus'},
                  {id: 40, label: 'Sorakulutuskerros'},
                  {id: 50, label: 'Muut pinnoitteet'}
                ]
              },
              {label: "vihjetieto", type: 'checkbox', defaultValue: "0", publicId: "suggest_box", values: [{id: 0, label: 'Tarkistettu'}, {id: 1, label: 'Vihjetieto'}], weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
            ]
          }
        ),
        isMultipleLinkSelectionAllowed: true,
        hasMunicipalityValidation: true
      },
      {
        typeId: assetType.trafficVolume,
        singleElementEventCategory: 'trafficVolume',
        multiElementEventCategory: 'trafficVolumes',
        layerName: 'trafficVolume',
        title: 'Liikennemäärä',
        newTitle: 'Uusi liikennemäärä',
        className: 'traffic-volume',
        unit: 'ajoneuvoa/vuorokausi',
        isSeparable: false,
        allowComplementaryLinks: false,
        editControlLabels: {
          title: '',
          enabled: 'Liikennemäärä',
          disabled: 'Ei tiedossa',
          showUnit: true
        },
        label: new LinearAssetLabel(),
        authorizationPolicy: new ReadOnlyAuthorizationPolicy(),
        isVerifiable: true,
        hasMunicipalityValidation: true
      },
      {
        typeId: assetType.massTransitLane,
        defaultValue: 1,
        singleElementEventCategory: 'massTransitLane',
        multiElementEventCategory: 'massTransitLanes',
        layerName: 'massTransitLanes',
        title: 'Joukkoliikennekaista',
        newTitle: 'Uusi joukkoliikennekaista',
        className: 'mass-transit-lane',
        isSeparable: true,
        allowComplementaryLinks: true,
        editControlLabels: {
          title: 'Kaista',
          enabled: 'Joukkoliikennekaista',
          disabled: 'Ei joukkoliikennekaistaa'
        },
        authorizationPolicy: new LinearStateRoadAuthorizationPolicy(),
        isVerifiable: true,
        hasMunicipalityValidation: true,
        isMultipleLinkSelectionAllowed: true,
        form: new DynamicAssetForm({
          fields: [
            {label: "", type: 'time_period', publicId: "public_validity_period", weight: 1}
          ]
        })
      },
      {
        typeId: assetType.winterSpeedLimit,
        singleElementEventCategory: 'winterSpeedLimit',
        multiElementEventCategory: 'winterSpeedLimits',
        layerName: 'winterSpeedLimits',
        title: 'Talvinopeusrajoitus',
        newTitle: 'Uusi talvinopeusrajoitus',
        className: 'winter-speed-limits',
        unit: 'km/h',
        isSeparable: true,
        allowComplementaryLinks: true,
        editControlLabels: {
          title: 'Rajoitus',
          enabled: 'Talvinopeusrajoitus',
          disabled: 'Ei talvinopeusrajoitusta',
          showUnit: true
        },
        possibleValues: [100, 80, 70, 60],
        style : new WinterSpeedLimitStyle(),
        isVerifiable: false,
        isMultipleLinkSelectionAllowed: true,
        authorizationPolicy: new LinearAssetAuthorizationPolicy(),
        minZoomForContent: oneKmZoomLvl,
        label: new WinterSpeedLimitLabel()
      },
      {
        typeId: assetType.prohibition,
        singleElementEventCategory: 'prohibition',
        multiElementEventCategory: 'prohibitions',
        layerName: 'prohibition',
        title: 'Ajoneuvokohtaiset rajoitukset',
        newTitle: 'Uusi ajoneuvokohtainen rajoitus',
        className: 'prohibition',
        isSeparable: true,
        allowComplementaryLinks: true,
        editControlLabels: {
          title: 'Rajoitus',
          enabled: 'Rajoitus',
          disabled: 'Ei rajoitusta'
        },
        isVerifiable: true,
        isMultipleLinkSelectionAllowed: true,
        authorizationPolicy: new LinearAssetAuthorizationPolicy(),
        hasMunicipalityValidation: true,
        readOnlyLayer: TrafficSignReadOnlyLayer,
        label: new SuggestionLabel()
      },
      {
        typeId: assetType.hazardousMaterialTransportProhibition,
        singleElementEventCategory: 'hazardousMaterialTransportProhibition',
        multiElementEventCategory: 'hazardousMaterialTransportProhibitions',
        layerName: 'hazardousMaterialTransportProhibition',
        title: 'VAK-rajoitus',
        newTitle: 'Uusi VAK-rajoitus',
        className: 'hazardousMaterialTransportProhibition',
        isSeparable: true,
        allowComplementaryLinks: true,
        editControlLabels: {
          title: 'VAK-rajoitus',
          enabled: 'Rajoitus',
          disabled: 'Ei rajoitusta'
        },
        isVerifiable: true,
        hasInaccurate: true,
        isMultipleLinkSelectionAllowed: true,
        authorizationPolicy: new LinearAssetAuthorizationPolicy(),
        hasMunicipalityValidation: true,
        readOnlyLayer: TrafficSignReadOnlyLayer,
        label: new SuggestionLabel()
      },
      {
        typeId: assetType.europeanRoads,
        singleElementEventCategory: 'europeanRoad',
        multiElementEventCategory: 'europeanRoads',
        layerName: 'europeanRoads',
        title: 'Eurooppatienumero',
        newTitle: 'Uusi eurooppatienumero',
        className: 'european-road',
        unit: '',
        isSeparable: false,
        allowComplementaryLinks: false,
        editControlLabels: {
          title: '',
          enabled: 'Eurooppatienumero(t)',
          disabled: 'Ei eurooppatienumeroa'
        },
        authorizationPolicy: new LinearStateRoadAuthorizationPolicy(),
        label: new LinearAssetLabelMultiValues(),
        isVerifiable: false,
        isMultipleLinkSelectionAllowed: true
      },
      {
        typeId: assetType.exitNumbers,
        singleElementEventCategory: 'exitNumber',
        multiElementEventCategory: 'exitNumbers',
        layerName: 'exitNumbers',
        title: 'Liittymänumero',
        newTitle: 'Uusi liittymänumero',
        className: 'exit-number',
        unit: '',
        isSeparable: false,
        allowComplementaryLinks: false,
        editControlLabels: {
          title: '',
          enabled: 'Liittymänumero(t)',
          disabled: 'Ei liittymänumeroa'
        },
        label: new LinearAssetLabelMultiValues(),
        isVerifiable: false,
        authorizationPolicy: new LinearAssetAuthorizationPolicy(),
        isMultipleLinkSelectionAllowed: true
      },
      {
        typeId: assetType.maintenanceRoad,
        singleElementEventCategory: 'maintenanceRoad',
        multiElementEventCategory: 'maintenanceRoads',
        layerName: 'maintenanceRoad',
        title: 'Rautateiden huoltotie',
        newTitle: 'Uusi rautateiden huoltotie',
        className: 'maintenanceRoad',
        isSeparable: false,
        unit: '',
        allowComplementaryLinks: true,
        editControlLabels: {
          title: '',
          enabled: 'Huoltotie',
          disabled: 'Ei huoltotietä'
        },
          form: new DynamicAssetForm({
              fields : [{
              label: 'Käyttöoikeus', type: 'single_choice', publicId: "huoltotie_kayttooikeus", defaultValue: "99",
                  values: [
                          {id: 1, label: 'Tieoikeus'},
                          {id: 2, label: 'Tiekunnan osakkuus'},
                          {id: 3, label: 'LiVin hallinnoimalla maa-alueella'},
                          {id: 4, label: 'Kevyen liikenteen väylä'},
                          {id: 6, label: 'Muu sopimus'},
                          {id: 9, label: 'Potentiaalinen käyttöoikeus'},
                          {id: 99, label: 'Tuntematon'}
                          ], weight: 1},
          {label: 'Huoltovastuu', type: 'single_choice', publicId: "huoltotie_huoltovastuu", defaultValue: "1", values: [{id: 1, label: 'LiVi'}, {id: 2, label: 'Muu'}, {id: 99, label: 'Ei tietoa'}], weight: 2},
          {label: "Tiehoitokunta", type: 'text', publicId: "huoltotie_tiehoitokunta", weight: 3},
          {label: "Tarkistettu", type: 'checkbox', publicId: "huoltotie_tarkistettu", defaultValue: "0", values: [{id: 0, label: 'Ei tarkistettu'}, {id: 1, label: 'Tarkistettu'}], weight: 4},
          {label: "Vihjetieto", type: 'checkbox', publicId: "suggest_box", defaultValue: "0", values: [{id: 0, label: 'Tarkistettu'}, {id: 1, label: 'Vihjetieto'}], weight: 5, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}]
        }),
        style: new ServiceRoadStyle(),
        label : new ServiceRoadLabel(),
        isVerifiable: false,
        layer : ServiceRoadLayer,
        collection: ServiceRoadCollection,
        authorizationPolicy: new ServiceRoadAuthorizationPolicy(),
        isMultipleLinkSelectionAllowed: true
      },
      {
        typeId: assetType.numberOfLanes,
        singleElementEventCategory: 'laneCount',
        multiElementEventCategory: 'laneCounts',
        layerName: 'numberOfLanes',
        title: 'Kaistojen lukumäärä',
        newTitle: 'Uusi kaistojen lukumäärä',
        className: 'lane-count',
        unit: 'kpl / suunta',
        isSeparable: true,
        allowComplementaryLinks: true,
        editControlLabels: {
          title: 'Lukumäärä',
          enabled: 'Kaistojen lukumäärä / suunta',
          disabled: 'Ei tietoa'
        },
        label: new LinearAssetLabel(),
        isVerifiable: true,
        authorizationPolicy: new LinearAssetAuthorizationPolicy(),
        isMultipleLinkSelectionAllowed: true,
        hasMunicipalityValidation: true,
        minZoomForContent: oneKmZoomLvl
      },
      {
        typeId: assetType.careClass,
        singleElementEventCategory: 'careClass',
        multiElementEventCategory: 'careClasses',
        layerName: 'careClass',
        title: 'Hoitoluokat',
        newTitle: 'Uusi hoitoluokka',
        className: 'careClass',
        isSeparable: false,
        unit: '',
        allowComplementaryLinks: true,
        editControlLabels: {
                title: 'Hoitoluokka',
                enabled: 'Hoitoluokka',
                disabled: 'Ei hoitoluokkaa'
            },
        form: new DynamicAssetForm({
                    fields : [
                        {
                            label: 'Talvihoitoluokka', type: 'single_choice', publicId: "hoitoluokat_talvihoitoluokka", defaultValue: "20",
                            values: [
                                {hidden: function() {return true;} , id: 1, label: '(IsE) Liukkaudentorjunta ilman toimenpideaikaa'},
                                {hidden: function() {return true;}, id: 2, label: '(Is) Normaalisti aina paljaana'},
                                {hidden: function() {return true;}, id: 3, label: '(I) Normaalisti paljaana'},
                                {hidden: function() {return true;}, id: 4, label: '(Ib) Pääosin suolattava, ajoittain hieman liukas'},
                                {hidden: function() {return true;}, id: 5, label: '(Ic) Pääosin hiekoitettava, ohut lumipolanne sallittu'},
                                {hidden: function() {return true;}, id: 6, label: '(II) Pääosin lumipintainen'},
                                {hidden: function() {return true;}, id: 7, label: '(III) Pääosin lumipintainen, pisin toimenpideaika'},
                                {hidden: function() {return true;}, id: 8, label: '(L) Kevyen liikenteen laatukäytävät'},
                                {hidden: function() {return true;}, id: 9, label: '(K1) Melko vilkkaat kevyen liikenteen väylät'},
                                {hidden: function() {return true;}, id: 10, label: '(K2) Kevyen liikenteen väylien perus talvihoitotaso'},
                                {hidden: function() {return true;}, id: 11, label: '(ei talvih.) Kevyen liikenteen väylät, joilla ei talvihoitoa'},
                                {id: 20, label: 'Pääkadut ja vilkkaat väylät'},
                                {id: 30, label: 'Kokoojakadut'},
                                {id: 40, label: 'Tonttikadut'},
                                {id: 50, label: 'A-luokan väylät'},
                                {id: 60, label: 'B-luokan väylät'},
                                {id: 70, label: 'C-luokan väylät'}
                            ]
                        },
                        {
                            label: 'Viherhoitoluokka', type: 'hidden_read_only_number', publicId: "hoitoluokat_viherhoitoluokka",
                            values: [
                                {id: 1, label: '(N1) 2-ajorataiset tiet'},
                                {id: 2, label: '(N2) Valta- ja kantatiet sekä vilkkaat seututiet'},
                                {id: 3, label: '(N3) Muut tiet'},
                                {id: 4, label: '(T1) Puistomainen taajamassa'},
                                {id: 5, label: '(T2) Luonnonmukainen taajamassa'},
                                {id: 6, label: '(E1) Puistomainen erityisalue'},
                                {id: 7, label: '(E2) Luonnonmukainen erityisalue'},
                                {id: 8, label: '(Y) Ympäristötekijä'}]
                        }
                    ]
                }),
        isVerifiable: false,
        authorizationPolicy: new LinearStateRoadAuthorizationPolicy(),
        layer: CareClassLayer,
        style: new CareClassStyle(),
        collection: CareClassCollection
      },
      {
        typeId: assetType.carryingCapacity,
        singleElementEventCategory: 'carryingCapacity',
        multiElementEventCategory: 'carryingCapacity',
        layerName: 'carryingCapacity',
        title: 'Kantavuus',
        newTitle: 'Uusi Kantavuus',
        className: 'carrying-capacity',
        unit: '',
        isSeparable: false,
        allowComplementaryLinks: false,
        editControlLabels: {
          title: 'Kantavuus',
          enabled: 'Kantavuus',
          disabled: 'Ei Kantavuutta'
        },
        style: new CarryingCapacityStyle(),
        layer: CarryingCapacityLayer,
        saveCondition: function (fields) {
          return _.isEmpty(fields) || _.some(fields, function (field) {
            var fieldPropertyType = field.getPropertyValue().propertyType;
            return field.hasValue() && (fieldPropertyType === "integer" || fieldPropertyType === "single_choice" && field.getValue() !== '999');
          });
        },
        authorizationPolicy: new LinearStateRoadAuthorizationPolicy(),
        isVerifiable: false,
        form: new DynamicAssetForm({
          fields: [
            {label: "Kevätkantavuus", type: 'integer', publicId: "kevatkantavuus", unit: "MN/m<sup>2</sup>", weight: 1},
            {label: "Routivuuskerroin", type: 'single_choice', publicId: "routivuuskerroin",
              values: [{id: 40, label: "40 Erittäin routiva"},
                {id: 50, label: "50 Väliarvo 50...60"},
                {id: 60, label: "60 Routiva"},
                {id: 70, label: "70 Väliarvo 60...80"},
                {id: 80, label: "80 Routimaton"},
                {id: 999, label: 'Ei tietoa'}], weight: 2, defaultValue: "999"
            },
            {label: "Mittauspäivä", type: 'date', publicId: "mittauspaiva", weight: 3}
          ]
        })
      },
      {
        typeId: assetType.roadWorksAsset,
        singleElementEventCategory: 'roadWorksAsset',
        multiElementEventCategory: 'roadsWorksAsset',
        layerName: 'roadWork',
        title: 'Tietyöt',
        newTitle: 'Uusi tietyöt',
        className: 'road-works-asset',
        isSeparable: true,
        allowComplementaryLinks: true,
        editControlLabels: {
          title: 'Tietyöt',
          enabled: 'Tietyö',
          disabled: 'Ei tietyötä',
          additionalInfo: 'Tuleva/mennyt tietyö'
        },
        authorizationPolicy: new LinearStateRoadAuthorizationPolicy(),
        isVerifiable: false,
        style: new RoadWorkStyle(),
        label: new LinearAssetWithSuggestLayer(),
        form: new DynamicAssetForm ( {
          fields : [
            {label: 'Työn tunnus', publicId: 'tyon_tunnus', type: 'text', weight: 1},
            {label: 'Arvioitu kesto', publicId: 'arvioitu_kesto', type: 'date_period', required: true, multiElement: false, weight: 2},
            {label: "Vihjetieto", type: 'checkbox', publicId: "suggest_box", defaultValue: "0", values: [{id: 0, label: 'Tarkistettu'}, {id: 1, label: 'Vihjetieto'}], weight: 3, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
          ]
        }),
        isMultipleLinkSelectionAllowed: true,
        saveCondition: function (fields) {
          var datePeriodField = _.filter(fields, function(field) { return field.getPropertyValue().propertyType === 'date_period'; });

          return _.every(datePeriodField, function (date) {
            return date.hasValue() ? isEndDateAfterStartdate(date) : true;
          });
        },
        hasMunicipalityValidation: false,
        readOnlyLayer: TrafficSignReadOnlyLayer
      },
      {
        typeId: assetType.parkingProhibition,
        defaultValue: 1,
        singleElementEventCategory: 'parkingProhibition',
        multiElementEventCategory: 'parkingProhibitions',
        layerName: 'parkingProhibition',
        title: 'Pysäköintikielto',
        newTitle: 'Uusi Pysäköintikielto',
        className: 'parking-prohibition',
        isSeparable: true,
        allowComplementaryLinks: false,
        editControlLabels: {
          title: 'Pysäköintikielto',
          enabled: 'Pysäköintikielto',
          disabled: 'Ei pysäköintikieltoa'
        },
        authorizationPolicy: new LinearStateRoadAuthorizationPolicy(),
        isVerifiable: false,
        style: new ParkingProhibitionStyle(),
        form: new DynamicAssetForm ( {
          fields : [
            {
              label: 'Rajoitus', required: 'required', type: 'single_choice', publicId: "parking_prohibition", defaultValue: "1", weight: 1,
              values: [
                {id: 1, label: 'Pysähtyminen kielletty'},
                {id: 2, label: 'Pysäköinti kielletty'}
              ]
            },
            {label: "", type: 'time_period', publicId: "parking_validity_period", weight: 2}
          ]
        }),
        isMultipleLinkSelectionAllowed: true,
        hasMunicipalityValidation: true,
        readOnlyLayer: TrafficSignReadOnlyLayer,
        minZoomForContent: oneKmZoomLvl
      },
      {
        typeId: assetType.cyclingAndWalking,
        singleElementEventCategory: 'cyclingAndWalking',
        multiElementEventCategory: 'cyclingAndWalkings',
        layerName: 'cyclingAndWalking',
        title: 'Käpy tietolaji',
        newTitle: 'Uusi Käpy tietolaji',
        className: 'cycling-and-walking',
        isSeparable: true,
        allowComplementaryLinks: true,
        editControlLabels: {
          title: 'Käpy tietolaji',
          enabled: 'Käpy tietolaji',
          disabled: 'Ei käpy tietolaji'
        },
        authorizationPolicy: new CyclingAndWalkingAuthorizationPolicy(),
        isVerifiable: false,
        style: new CyclingAndWalkingStyle(),
        form: new DynamicAssetForm ( {
          fields : [
            {
              label: 'Käpy tietolaji', required: 'required', type: 'single_choice', publicId: "cyclingAndWalking_type", defaultValue: "99", weight: 1,
              values: [
                {id: 99, label: 'Ei tietoa', disabled: true },
                {id: 1 , label:'Pyöräily ja kävely kielletty'},
                {id: 2 , label:'Pyöräily kielletty'},
                {id: 3 , label:'Jalankulun ja pyöräilyn väylä', hidden: cyclingAndWalkingValidator },
                {id: 4 , label:'Maantie tai yksityistie', hidden: cyclingAndWalkingValidator},
                {id: 5 , label:'Katu', hidden: cyclingAndWalkingValidator},
                {id: 6 , label:'Pyöräkatu'},
                {id: 7 , label:'Kylätie'},
                {id: 9 , label:'Pihakatu'},
                {id: 8 , label:'Kävelykatu'},
                {id: 10 , label:'Pyöräkaista'},
                {id: 11 , label:'Pyörätie'},
                {id: 12 , label:'Kaksisuuntainen pyörätie'},
                {id: 13 , label:'Yhdistetty pyörätie ja jalkakäytävä, yksisuuntainen pyörille'},
                {id: 14 , label:'Yhdistetty pyörätie ja jalkakäytävä, kaksisuuntainen pyörille'},
                {id: 16 , label:'Puistokäytävä'},
                {id: 15 , label:'Jalkakäytävä'},
                {id: 17 , label:'Pururata'},
                {id: 18 , label:'Ajopolku', hidden: cyclingAndWalkingValidator},
                {id: 19 , label:'Polku'},
                {id: 20 , label:'Lossi tai lautta'}
              ]
            }
          ]
        }),
        isMultipleLinkSelectionAllowed: true,
        hasMunicipalityValidation: true,
        readOnlyLayer: TrafficSignReadOnlyLayer,
        minZoomForContent: oneKmZoomLvl,
        saveCondition: function (fields) {

            return _.isEmpty(fields) ||  _.some(fields, function (field) {
              var publicId = field.getPropertyValue().publicId;
              return field.hasValue() && (publicId === "cyclingAndWalking_type" && field.getValue() !== "99");
            });
        }
      },
      {
        typeId: assetType.laneModellingTool,
        singleElementEventCategory: 'laneModellingTool',
        multiElementEventCategory: 'lanesModellingTool',
        layerName: 'laneModellingTool',
        title: 'Kaistan mallinnustyökalu',
        newTitle: 'Uusi kaistan mallinnustyökalu',
        className: 'lane-modelling-tool',
        authorizationPolicy: new LinearAssetAuthorizationPolicy(),
        editControlLabels: {
          title: 'Kaistan mallinnustyökalu',
        },
        isSeparable: false,
        allowComplementaryLinks: true,
        isVerifiable: false,
        style: new LaneModellingStyle(),
        form: new LaneModellingForm({
          fields : [
            {
              label: 'Kaista', type: 'read_only_number', publicId: "lane_code", weight: 6
            },
            {
              label: 'Kaistan tyypi', required: 'required', type: 'single_choice', publicId: "lane_type",
              values: [
                {id: 2, label: 'Ohituskaista'},
                {id: 3, label: 'Kääntymiskaista oikealle'},
                {id: 4, label: 'Kääntymiskaista vasemmalle'},
                {id: 5, label: 'Lisäkaista suoraan ajaville'},
                {id: 6, label: 'Liittymiskaista'},
                {id: 7, label: 'Erkanemiskaista'},
                {id: 8, label: 'Sekoittumiskaista'},
                {id: 9, label: 'Joukkoliikenteen kaista / taksikaista'},
                {id: 10, label: 'Raskaan liikenteen kaista'},
                {id: 11, label: 'Vaihtuvasuuntainen kaista'},
                {id: 20, label: 'Yhdistetty jalankulun ja pyöräilyn kaista'},
                {id: 21, label: 'Jalankulun kaista'},
                {id: 22, label: 'Pyöräilykaista'},
              ],  defaultValue: "2", weight: 7
            },
            {
              label: 'Kaista jatkuvuus', required: 'required', type: 'single_choice', publicId: "lane_continuity", defaultValue: "1", weight: 8,
              values: [
                {id: 1, label: 'Jatkuva'},
                {id: 2, label: 'Jatkuu toisella kaistanumerolla'},
                {id: 3, label: 'Kääntyvä'},
                {id: 4, label: 'Päättyvä'},
                {id: 5, label: 'Jatkuva, osoitettu myös oikealle kääntyville'},
                {id: 6, label: 'Jatkuva, osoitettu myös vasemmalle kääntyville'},
              ]
            },
            {
              label: 'Kaista ominaisuustieto', type: 'text', publicId: "lane_information", weight: 9
            },
            {
              label: 'Alkupvm', type: 'date', publicId: "start_date", weight: 10
            },
            {
              label: 'Loppupvm', type: 'date', publicId: "end_date", weight: 11
            }
          ]
        }),
        saveCondition:function (lanes) {
          var isValidLane = function (fields) {
            var isValidDatePeriod = function (fields) {
              var isValidDate = true;
              var startDate = Property.getPropertyByPublicId(fields, 'start_date');
              var endDate = Property.getPropertyByPublicId(fields, 'end_date');

              if (startDate && endDate && !_.isEmpty(startDate.values) && !_.isEmpty(endDate.values) && !_.isUndefined(startDate.values[0]) && !_.isUndefined(endDate.values[0]))
                isValidDate = isValidPeriodDate(dateExtract(_.head(startDate.values).value), dateExtract(_.head(endDate.values).value));
              return isValidDate;
            };

            var isValidRoadAddress = function (fields) {
              var isValidRoadAddress = true;
              var initialRoadAddressesFields = Property.filterPropertiesByPropertyType(fields, 'read_only_number');
              var initialRoadPartNumber = Property.getPropertyByPublicId(initialRoadAddressesFields, 'initial_road_part_number');
              var initialDistance = Property.getPropertyByPublicId(initialRoadAddressesFields, 'initial_distance');

              if (!_.isUndefined(initialRoadPartNumber)) {
                var roadAddressesFields = Property.filterPropertiesByPropertyType(fields, 'number');
                var endRoadPartNumber = Property.getPropertyByPublicId(roadAddressesFields, 'end_road_part_number');
                var endDistance = Property.getPropertyByPublicId(roadAddressesFields, 'end_distance');

                if (_.isUndefined(endRoadPartNumber) || _.isUndefined(endDistance) || _.isEmpty(endRoadPartNumber.values) ||
                  _.isEmpty(endDistance.values) || _.isUndefined(endRoadPartNumber.values[0]) || _.isUndefined(endDistance.values[0]) ||
                  _.head(endRoadPartNumber.values).value < _.head(initialRoadPartNumber.values).value ||
                  (_.head(endRoadPartNumber.values).value == _.head(initialRoadPartNumber.values).value && _.head(endDistance.values).value <= _.head(initialDistance.values).value)) {
                  isValidRoadAddress = false;
                }
              }
              return isValidRoadAddress;
            };

            return isValidDatePeriod(fields) && isValidRoadAddress(fields);
          };

          return !_.some(_.map(lanes, function (lane) {
            return isValidLane(lane.properties);
          }), function (boolean) {
            return boolean === false;
          });
        },
        selected: SelectedLaneModelling,
        collection: LaneModellingCollection,
        layer: LaneModellingLayer,
        label: new LaneModellingLabel()
      }
    ];

    var experimentalLinearAssetSpecs = [
      {
        typeId: assetType.trSpeedLimits,
        singleElementEventCategory: 'trSpeedLimit',
        multiElementEventCategory: 'trSpeedLimits',
        layerName: 'trSpeedLimits',
        title: 'Tierekisteri nopeusrajoitus',
        newTitle: 'Uusi nopeusrajoitus',
        className: 'tr-speed-limits',
        unit: 'km/h',
        isSeparable: true,
        allowComplementaryLinks: false,
        editControlLabels: {
          title: '',
          enabled: 'Nopeusrajoitus',
          disabled: 'Tuntematon'
        },
        label: new TRSpeedLimitAssetLabel(),
        readOnlyLayer: TrafficSignReadOnlyLayer,
        style: new TRSpeedLimitStyle(),
        authorizationPolicy: new ReadOnlyAuthorizationPolicy()
      }
    ];

    var pointAssetSpecs = [
      {
        typeId: assetType.pedestrianCrossings,
        layerName: 'pedestrianCrossings',
        title: 'Suojatie',
        allowComplementaryLinks: true,
        newAsset: { propertyData: [
            {'name': "Vihjetieto", 'propertyType': 'checkbox', 'publicId': "suggest_box", values: [ {propertyValue: 0} ]}
        ]},
        isSuggestedAsset: true,
        legendValues: [
          {symbolUrl: 'images/point-assets/point_blue.svg', label: 'Suojatie'},
          {symbolUrl: 'images/point-assets/point_red.svg', label: 'Geometrian ulkopuolella'}
        ],
        formLabels: {
          singleFloatingAssetLabel: 'suojatien',
          manyFloatingAssetsLabel: 'suojatiet',
          newAssetLabel: 'suojatie'
        },
        hasMunicipalityValidation: true,
        saveCondition: saveConditionWithSuggested,
        hasInaccurate: true,
        readOnlyLayer: TrafficSignReadOnlyLayer,
        authorizationPolicy: new PointStateRoadAuthorizationPolicy(),
        showRoadLinkInfo: true,
        label: new SuggestionLabel()
      },
      {
        typeId: assetType.obstacles,
        layerName: 'obstacles',
        title: 'Esterakennelma',
        allowComplementaryLinks: true,
        newAsset: { propertyData: [
            {'name': 'Esterakennelma', 'propertyType': 'single_choice', 'publicId': "esterakennelma", values: [ {propertyValue: 1, propertyDisplayValue: ""} ] },
            {'name': "Vihjetieto", 'propertyType': 'checkbox', 'publicId': "suggest_box", values: [ {propertyValue: 0} ]}
        ]},
        legendValues: [
          {symbolUrl: 'images/point-assets/point_blue.svg', label: 'Suljettu yhteys'},
          {symbolUrl: 'images/point-assets/point_green.svg', label: 'Avattava puomi'},
          {symbolUrl: 'images/point-assets/point_red.svg', label: 'Geometrian ulkopuolella'}
        ],
        formLabels: {
          singleFloatingAssetLabel: 'esterakennelman',
          manyFloatingAssetsLabel: 'esterakennelmat',
          newAssetLabel: 'esterakennelma'
        },
        authorizationPolicy: new PointAssetAuthorizationPolicy(),
        form: ObstacleForm,
        saveCondition: saveConditionWithSuggested,
        hasMunicipalityValidation: true,
        roadCollection: ObstaclesRoadCollection,
        showRoadLinkInfo: true,
        isSuggestedAsset: true,
        label: new SuggestionLabel()
      },
      {
        typeId: assetType.railwayCrossings,
        layerName: 'railwayCrossings',
        title: 'Rautatien tasoristeys',
        allowComplementaryLinks: true,
        newAsset: { safetyEquipment: 1, propertyData: [
            {'name': "Turvavarustus", 'propertyType': 'single_choice', 'publicId': "turvavarustus", values: [ {propertyValue: 0} ]},
            {'name': "Nimi", 'propertyType': 'text', 'publicId': "rautatien_tasoristeyksen_nimi", values: [ {propertyValue: ''} ]},
            {'name': "Tasoristeystunnus", 'propertyType': 'text', 'publicId': "tasoristeystunnus", values: [ {propertyValue: ''} ]},
            {'name': "Vihjetieto", 'propertyType': 'checkbox', 'publicId': "suggest_box", values: [ {propertyValue: 0} ]}
        ]},
        legendValues: [
          {symbolUrl: 'images/point-assets/point_blue.svg', label: 'Rautatien tasoristeys'},
          {symbolUrl: 'images/point-assets/point_red.svg', label: 'Geometrian ulkopuolella'}
        ],
        formLabels: {
          singleFloatingAssetLabel: 'tasoristeyksen',
          manyFloatingAssetsLabel: 'tasoristeykset',
          newAssetLabel: 'tasoristeys'
        },
        saveCondition: function(selectedAsset, authorizationPolicy) {
          var selected = selectedAsset.get();
          var propertyValue = parseInt(_.find(selected.propertyData, function(prop){ return prop.publicId === 'turvavarustus'; }).values[0].propertyValue);
          return (propertyValue ? propertyValue !== 0 : false) && (!(selected.isSuggested && authorizationPolicy.isMunicipalityMaintainer()) || authorizationPolicy.isOperator());
        },
        authorizationPolicy: new PointAssetAuthorizationPolicy(),
        form: RailwayCrossingForm,
        isSuggestedAsset: true,
        hasMunicipalityValidation: true,
        label: new SuggestionLabel(),
        showRoadLinkInfo: true
      },
      {
        typeId: assetType.directionalTrafficSigns,
        layerName: 'directionalTrafficSigns',
        title: 'Opastustaulu',
        allowComplementaryLinks: false,
        newAsset: { validityDirection: 2, propertyData: [
            {'name': "Teksti", 'propertyType': 'text', 'publicId': "opastustaulun_teksti", values: [ {propertyValue: ""} ]},
            {'name': "Vihjetieto", 'propertyType': 'checkbox', 'publicId': "suggest_box", values: [ {propertyValue: 0} ]}
          ]},
        legendValues: [
          {symbolUrl: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-directional-traffic-sign.svg', label: 'Opastustaulu'},
          {symbolUrl: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-warning-directional-traffic-sign.svg', label: 'Geometrian ulkopuolella'}
        ],
        formLabels: {
          singleFloatingAssetLabel: 'opastustaulun',
          manyFloatingAssetsLabel: 'opastustaulut',
          newAssetLabel: 'opastustaulu'
        },
        authorizationPolicy: new PointAssetAuthorizationPolicy(),
        form: DirectionalTrafficSignForm,
        isSuggestedAsset: true,
        saveCondition: saveConditionWithSuggested,
        hasMunicipalityValidation: true,
        label: new SuggestionLabel(),
        showRoadLinkInfo: true
      },
      {
        typeId: assetType.servicePoints,
        layerName: 'servicePoints',
        title: 'Palvelupiste',
        allowComplementaryLinks: false,
        allowGrouping: true,
        groupingDistance: Math.pow(3, 2),
         newAsset: { services: [], propertyData: [
             {'name': "Vihjetieto", 'propertyType': 'checkbox', 'publicId': "suggest_box", values: [ {propertyValue: 0} ]}
           ] },
        legendValues: [
          {symbolUrl: 'images/service_points/parkingGarage.png', label: 'Pysäköintitalo'},
          {symbolUrl: 'images/service_points/parking.png', label: 'Pysäköintialue'},
          {symbolUrl: 'images/service_points/railwayStation2.png', label: 'Merkittävä rautatieasema'},
          {symbolUrl: 'images/service_points/railwayStation.png', label: 'Vähäisempi rautatieasema'},
          {symbolUrl: 'images/service_points/subwayStation.png', label: 'Metroasema'},
          {symbolUrl: 'images/service_points/busStation.png', label: 'Linja-autoasema'},
          {symbolUrl: 'images/service_points/airport.png', label: 'Lentokenttä'},
          {symbolUrl: 'images/service_points/ferry.png', label: 'Laivaterminaali'},
          {symbolUrl: 'images/service_points/taxiStation.png', label: 'Taksiasema'},
          {symbolUrl: 'images/service_points/picnicSite.png', label: 'Lepoalue'},
          {symbolUrl: 'images/service_points/customsControl.png', label: 'Tulli'},
          {symbolUrl: 'images/service_points/borderCrossingLeftMenu.png', label: 'Rajanylityspaikka', cssClass: 'border-crossing'},
          {symbolUrl: 'images/service_points/loadingTerminalForCarsLeftMenu.png', label: 'Autojen lastausterminaali', cssClass: 'loading-terminal'},
          {symbolUrl: 'images/service_points/parkingAreaBusesAndTrucksLeftMenu.png', label: 'Linja- ja kuorma-autojen pysäköintialue', cssClass: 'parking-area'},
          {symbolUrl: 'images/service_points/chargingPointElectricCarsLeftMenu.png', label: 'Sähköautojen latauspiste', cssClass: 'charging-point'},
          {symbolUrl: 'images/service_points/culvert.png', label: 'Tierumpu', cssClass: 'culvert-point'}
        ],
        formLabels: {
          singleFloatingAssetLabel: 'palvelupisteen',
          manyFloatingAssetsLabel: 'palvelupisteet',
          newAssetLabel: 'palvelupiste'
        },
        label: new ServicePointLabel(Math.pow(3, 2)),
        authorizationPolicy: new ServicePointAuthorizationPolicy(),
        form: ServicePointForm,
        saveCondition: function (selectedAsset, authorizationPolicy) {
          return  saveConditionWithSuggested(selectedAsset, authorizationPolicy) &&
                 _.chain(selectedAsset.get().services)
                  .filter(function(x) {return x.serviceType === 19;})
                  .value()
                  .every(function (x) {return _.isUndefined(x.weightLimit) || !_.isNaN(x.weightLimit) && !!parseInt(x.weightLimit);});
        },
        isSuggestedAsset: true,
        hasMunicipalityValidation: true,
        showRoadLinkInfo: true
      },
      {
        typeId: assetType.trafficLights,
        layerName: 'trafficLights',
        title: 'Liikennevalot',
        allowComplementaryLinks: true,
        newAsset: {propertyData: [
            {'groupedId': 1, 'name': "Tyyppi", 'propertyType': 'single_choice', 'publicId': "trafficLight_type", values: [ {propertyValue: 1} ]},
            {'groupedId': 1, 'name': "Opastimen suhteellinen sijainti", 'propertyType': 'single_choice', 'publicId': "trafficLight_relative_position", values: [ {propertyValue: 1} ]},
            {'groupedId': 1, 'name': "Opastimen rakennelma", 'propertyType': 'single_choice', 'publicId': "trafficLight_structure", values: [ {propertyValue: 99} ]},
            {'groupedId': 1, 'name': "Alituskorkeus", 'propertyType': 'number', 'publicId': "trafficLight_height", values: []},
            {'groupedId': 1, 'name': "Äänimerkki", 'propertyType': 'single_choice', 'publicId': "trafficLight_sound_signal", values: [ {propertyValue: 99} ]},
            {'groupedId': 1, 'name': "Ajoneuvon tunnistus", 'propertyType': 'single_choice', 'publicId': "trafficLight_vehicle_detection", values: [ {propertyValue: 99} ]},
            {'groupedId': 1, 'name': "Painonappi", 'propertyType': 'single_choice', 'publicId': "trafficLight_push_button", values: [ {propertyValue: 99} ]},
            {'groupedId': 1, 'name': "Lisätieto", 'propertyType': 'text', 'publicId': "trafficLight_info", values: []},
            {'groupedId': 1, 'name': "Kaistan tyyppi", 'propertyType': 'single_choice', 'publicId': "trafficLight_lane_type", values: [ {propertyValue: 99} ]},
            {'groupedId': 1, 'name': "Kaista", 'propertyType': 'number', 'publicId': "trafficLight_lane", values: []},
            {'groupedId': 1, 'name': "Maastosijainti X", 'propertyType': 'number', 'publicId': "location_coordinates_x", values: [] },
            {'groupedId': 1, 'name': "Maastosijainti Y", 'propertyType': 'number', 'publicId': "location_coordinates_y", values: [] },
            {'groupedId': 1, 'name': "Kunta ID", 'propertyType': 'text', 'publicId': "trafficLight_municipality_id", values: []},
            {'groupedId': 1, 'name': "Tila", 'propertyType': 'single_choice', 'publicId': "trafficLight_state", values: [ {propertyValue: 3} ]},
            {'groupedId': 1, 'name': "Vihjetieto", 'propertyType': 'checkbox', 'publicId': "suggest_box", values: [ {propertyValue: 0} ]},
            {'groupedId': 1, 'name': "Suunta", 'propertyType': 'hidden', 'publicId': "bearing", values: []},
            {'groupedId': 1, 'name': "Sidecode", 'propertyType': 'hidden', 'publicId': "sidecode", values: [ {propertyValue: 2} ]}
        ]},
        isSuggestedAsset: true,
        legendValues: {
          oldValues: [
            {symbolUrl: 'images/point-assets/point_blue.svg', label: 'Liikennevalo'},
            {symbolUrl: 'images/point-assets/point_red.svg', label: 'Geometrian ulkopuolella'}
          ],
          newValues: [
            {symbolUrl: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow.svg', label: 'Opastinlaite'},
            {symbolUrl: 'src/resources/digiroad2/bundle/assetlayer/images/no-direction.svg', label: 'Useita erisuuntaisia opastinlaitteita'}
          ]
        },
        formLabels: {
          singleFloatingAssetLabel: 'liikennevalojen',
          manyFloatingAssetsLabel: 'liikennevalot',
          newAssetLabel: 'liikennevalo'
        },
        hasMunicipalityValidation: true,
        saveCondition: function (selectedAsset, authorizationPolicy) {
          var fields = selectedAsset.get().propertyData;

          var lanesValidationForMultiple = function (fields) {
            var lanePublicId = 'trafficLight_lane';
            var laneTypePublicId = 'trafficLight_lane_type';

            var allLaneRelatedPropertiesVerification = _.map(_.filter(fields, function(field) { return field.publicId === lanePublicId; }), function (laneNumberProperty) {
              var laneTypeProperty = _.find(fields, function(field) { return field.publicId === laneTypePublicId && field.groupedId === laneNumberProperty.groupedId; });
              return lanesValidation(fields, laneNumberProperty, laneTypeProperty);
            });

            return _.every(allLaneRelatedPropertiesVerification);
          };

          var suggestedAssetCondition = _.every(_.map(_.filter(fields, function(asset) { return asset.publicId === "suggest_box"; }), function (suggestedProperty) {
            var suggestedBoxValue = !!parseInt(suggestedProperty.values[0].propertyValue);
            return !(suggestedBoxValue && authorizationPolicy.isMunicipalityMaintainer()) || authorizationPolicy.isOperator();
          }));

          var isValidNumericalFields = numericValidation(fields);
          var isValidLane = lanesValidationForMultiple(fields);

          return suggestedAssetCondition && isValidNumericalFields && isValidLane;
        },
        authorizationPolicy: new PointAssetAuthorizationPolicy(),
        form: TrafficLightForm,
        label: new SuggestionLabel(),
        showRoadLinkInfo: true,
        layer : TrafficRegulationLayer,
        lanePreview: true
      },
      {
        typeId: assetType.trafficSigns,
        layerName: 'trafficSigns',
        title: 'Liikennemerkit',
        allowComplementaryLinks: true,
        newAsset: { validityDirection: 2, propertyData: [
          {'name': 'Liikenteenvastainen', 'propertyType': 'single_choice', 'publicId': "opposite_side_sign", values: [] },
          {'name': 'Tyyppi', 'propertyType': 'single_choice', 'publicId': "trafficSigns_type", values: [ {propertyValue: 1} ] },
          {'name': 'Päämerkin teksti', 'propertyType': 'text', 'publicId': 'main_sign_text', values: []},
          {'name': "Arvo", 'propertyType': 'text', 'publicId': "trafficSigns_value", values: []},
          {'name': "Lisatieto", 'propertyType': 'text', 'publicId': "trafficSigns_info", values: []},
          {'name': "Sijaintitarkenne", 'propertyType': 'single_choice', 'publicId': "location_specifier", values: [{ propertyValue: 99 }]},
          {'name': "Rakenne", 'propertyType': 'single_choice', 'publicId': "structure", values: [{ propertyValue: 99 }]},
          {'name': "Kunto", 'propertyType': 'single_choice', 'publicId': "condition", values: [{ propertyValue: 99 }]},
          {'name': "Koko", 'propertyType': 'single_choice', 'publicId': "size", values: [{ propertyValue: 99 }]},
          {'name': "Korkeus", 'propertyType': 'number', 'publicId': "height", values: []},
          {'name': "Kalvon tyyppi", 'propertyType': 'single_choice', 'publicId': "coating_type", values: [{ propertyValue: 99 }]},
          {'name': "Kaista", 'propertyType': 'number', 'publicId': "lane", values: []},
          {'name': "Tila", 'propertyType': 'single_choice', 'publicId': "life_cycle", values: [ {propertyValue: 3} ]},
          {'name': "Merkin materiaali", 'propertyType': 'single_choice', 'publicId': "sign_material", values: [{ propertyValue: 99 }]},
          {'name': "Alkupäivämäärä", 'propertyType': 'date', 'publicId': "trafficSign_start_date", values: [] },
          {'name': "Loppupäivämäärä", 'propertyType': 'date', 'publicId': "trafficSign_end_date", values: [] },
          {'name': "Kaistan tyyppi", 'propertyType': 'single_choice', 'publicId': "lane_type", values: [{ propertyValue: 99 }] },
          {'name': "Vauriotyyppi", 'propertyType': 'single_choice', 'publicId': "type_of_damage", values: [{ propertyValue: 99 }] },
          {'name': "Korjauksen kiireellisyys", 'propertyType': 'single_choice', 'publicId': "urgency_of_repair", values: [{ propertyValue: 99 }] },
          {'name': "Arvioitu käyttöikä", 'propertyType': 'number', 'publicId': "lifespan_left", values: [] },
          {'name': "Kunnan ID", 'propertyType': 'text', 'publicId': "municipality_id", values: [] },
          {'name': "Maastokoordinaatti X", 'propertyType': 'number', 'publicId': "terrain_coordinates_x", values: [] },
          {'name': "Maastokoordinaatti Y", 'propertyType': 'number', 'publicId': "terrain_coordinates_y", values: [] },
          {'name': "Lisäkilpi", 'propertyType': 'additional_panel_type', 'publicId': "additional_panel", values: [], defaultValue:
                {panelType:53, panelInfo : "", panelValue : "", formPosition : "", text:"", size: 99, coating_type: 99, additional_panel_color: 99 }},
          {'name': "Lisää vanhan lain mukainen koodi", 'propertyType': 'checkbox', 'publicId': "old_traffic_code", values: [ {propertyValue: 0} ]},
          {'name': "Vihjetieto", 'propertyType': 'checkbox', 'publicId': "suggest_box", values: [ {propertyValue: 0} ]}
        ]},
        label: new TrafficSignLabel(Math.pow(3, 2)),
        collection: TrafficSignsCollection,
        allowGrouping: true,
        groupingDistance: Math.pow(3, 2), //geometry-calculations calculates the squared distance between two points, so give the grouping distance in meters x^2
        formLabels: {
          singleFloatingAssetLabel: 'liikennemerkin',
          manyFloatingAssetsLabel: 'liikennemerkit',
          newAssetLabel: 'liikennemerkki'
        },
        layer : TrafficRegulationLayer,
        authorizationPolicy: new PointStateRoadAuthorizationPolicy(),
        form: TrafficSignForm,
        hasMunicipalityValidation: true,
        isSuggestedAsset: true,
        saveCondition: function (selectedAsset, authorizationPolicy) {
          var possibleSpeedLimitsValues = [20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120];
          var validations = [
            { types: [1, 2, 3, 4], validate: function (someValue) { return /^\d+$/.test(someValue) && _.includes(possibleSpeedLimitsValues, parseInt(someValue)); }},
            { types: [8, 30, 31, 32, 33, 34, 35], validate: function (someValue) { return /^\d*\.?\d+$/.test(someValue) ; }}
          ];
          var lifecycleValidations = [
            { values: [4, 5], validate: function (startDate, endDate) { return !_.isUndefined(startDate) && !_.isUndefined(endDate) && endDate >= startDate; }}
          ];
          var fields = selectedAsset.get().propertyData;

          var opposite_side_sign =  _.find( fields, function(prop) { if (prop.publicId === "opposite_side_sign") return prop; });
          if (_.isUndefined(opposite_side_sign) || _.isUndefined(opposite_side_sign.values[0]) || opposite_side_sign.values[0].propertyValue === "") {
            selectedAsset.setPropertyByPublicId('opposite_side_sign', '0');
          }

          var functionFn = _.find(validations, function(validation){ return _.includes(validation.types, parseInt(Property.getPropertyValue('Tyyppi', selectedAsset.get())));});
          var suggestedBoxValue = !!parseInt(_.find(fields, function(asset) { return asset.publicId === "suggest_box"; }).values[0].propertyValue);
          var suggestedAssetCondition = !(suggestedBoxValue && authorizationPolicy.isMunicipalityMaintainer()) || authorizationPolicy.isOperator();
          var isValidFunc = functionFn ?  functionFn.validate(Property.getPropertyValue('Arvo', selectedAsset.get())) : true;

          /* Begin: Special validate for roadwork sign */
          var trafficSignTypeField = _.find(fields, function(field) { return field.publicId === 'trafficSigns_type'; });

          var trafficSignTypeExtracted = _.head(trafficSignTypeField.values).propertyValue;
          var startDateExtracted = dateValueExtract(fields, 'trafficSign_start_date');
          var endDateExtracted = dateValueExtract(fields, 'trafficSign_end_date');

          var roadworksTrafficCode = "85";
          var isValidaRoadWorkInfo = trafficSignTypeExtracted === roadworksTrafficCode && !_.isUndefined(startDateExtracted) && !_.isUndefined(endDateExtracted) ? endDateExtracted >= startDateExtracted : false;

          if (trafficSignTypeExtracted === roadworksTrafficCode)
            return isValidFunc && isValidaRoadWorkInfo && suggestedAssetCondition;
          /* End: Special validate for roadwork sign */

          var lifecycleField = _.find(fields, function(field) { return field.publicId === 'life_cycle'; });
          var lifecycleValidator = _.find(lifecycleValidations, function (validator) { return _.includes(validator.values, parseInt(_.head(lifecycleField.values).propertyValue)); });
          var validLifecycleDates = _.isUndefined(lifecycleValidator) ? true : lifecycleValidator.validate(startDateExtracted, endDateExtracted);

          var isValidNumericalFields = numericValidation(fields);

          var laneNumberProperty = _.find(fields, function(field) { return field.publicId === 'lane'; });
          var laneTypeProperty = _.find(fields, function(field) { return field.publicId === 'lane_type'; });

          var isValidLane = lanesValidation(fields, laneNumberProperty, laneTypeProperty);

          return isValidFunc && suggestedAssetCondition && validLifecycleDates && isValidNumericalFields && isValidLane;
        },
        readOnlyLayer: TrafficSignReadOnlyLayer,
        showRoadLinkInfo: true,
        lanePreview: true
      },
      {
        typeId: assetType.trHeightLimits,
        layerName: 'trHeightLimits',
        title: 'TR suurin sallittu korkeus',
        allowComplementaryLinks: true,
        allowGrouping: true,
        groupingDistance: Math.pow(5, 2), //geometry-calculations calculates the squared distance between two points, so give the grouping distance in meters x^2
        legendValues: [
          {symbolUrl: 'images/point-assets/point_blue.svg', label: 'Rajoitus'},
          {symbolUrl: 'images/point-assets/point_red.svg', label: 'Geometrian ulkopuolella'}
        ],
        formLabels: {
          title: 'Rajoitus',
          showUnit: true,
          manyFloatingAssetsLabel: 'rajoitus',
          singleFloatingAssetLabel: 'rajoitukset'
        },
        authorizationPolicy: new ReadOnlyAuthorizationPolicy(),
        nonModifiableBox: true,
        form: HeightLimitForm,
        label: new HeightLimitLabel(Math.pow(5, 2)),
        showRoadLinkInfo: true
      },
      {
        typeId: assetType.trWidthLimits,
        layerName: 'trWidthLimits',
        title: 'TR suurin sallittu leveys',
        allowComplementaryLinks: true,
        allowGrouping: true,
        groupingDistance: Math.pow(5, 2), //geometry-calculations calculates the squared distance between two points, so give the grouping distance in meters x^2
        legendValues: [
          {symbolUrl: 'images/point-assets/point_blue.svg', label: 'Rajoitus'},
          {symbolUrl: 'images/point-assets/point_red.svg', label: 'Geometrian ulkopuolella'}
        ],
        formLabels: {
          title: 'Rajoitus',
          showUnit: true,
          manyFloatingAssetsLabel: 'rajoitus',
          singleFloatingAssetLabel: 'rajoitukset'
        },
        authorizationPolicy: new ReadOnlyAuthorizationPolicy(),
        nonModifiableBox: true,
        form: WidthLimitForm,
        label: new WidthLimitLabel(Math.pow(5, 2)),
        showRoadLinkInfo: true
      }
    ];

    var groupedPointAssetSpecs = [
      {
        typeIds: assetGroups.trWeightGroup,
        layerName: 'trWeightLimits',
        title: 'TR painorajoitukset',
        allowComplementaryLinks: true,
        allowGrouping: false,
        legendValues: [
          {symbolUrl: 'images/point-assets/point_blue.svg', label: 'Rajoitus'},
          {symbolUrl: 'images/point-assets/point_red.svg', label: 'Geometrian ulkopuolella'}
        ],
        formLabels: {
          title: 'Painorajoitus',
          showUnit: true,
          manyFloatingAssetsLabel: 'rajoitus',
          singleFloatingAssetLabel: 'rajoitukset'
        },
        authorizationPolicy: new ReadOnlyAuthorizationPolicy(),
        nonModifiableBox: true,
        label: new WeightLimitLabel(),
        propertyData: [
          {'propertyTypeId': assetType.trWeightLimits, 'propertyType': 'number', 'publicId': "suurin_sallittu_massa_mittarajoitus", values: []},
          {'propertyTypeId': assetType.trTrailerTruckWeightLimits, 'propertyType': 'number', 'publicId': "yhdistelman_suurin_sallittu_massa", values: []},
          {'propertyTypeId': assetType.trAxleWeightLimits, 'propertyType': 'number', 'publicId': "suurin_sallittu_akselimassa", values: []},
          {'propertyTypeId': assetType.trBogieWeightLimits, 'propertyType': 'number', 'publicId': "suurin_sallittu_telimassa", values: []}
        ],
        showRoadLinkInfo: true
      }
    ];

    var assetTypeInfo = [
        {
            typeId: assetType.massTransitStop,
            title: 'Joukkoliikenteen pysäkki',
            layerName: "massTransitStop"
        },
        {
            typeId: assetType.speedLimit,
            title: 'Nopeusrajoitus',
            layerName: "speedLimit"
        },
        {
            typeId: assetType.manoeuvre,
            title: 'Kääntymisrajoitus',
            layerName: "manoeuvre"
        },
        {
            typeId: assetType.trWeightLimits,
            title: 'TR painorajoitukset',
            layerName: "trWeightLimits"
        }
    ];

    return {
      assetTypes : assetType,
      assetTypeInfo: assetTypeInfo.concat( _.map(linearAssetSpecs, function(asset) { return _.zipObject(['typeId', 'title'], [asset.typeId, asset.title]); }),
                                           _.map(pointAssetSpecs, function(asset) { return _.zipObject(['typeId', 'title'], [asset.typeId, asset.title]); })),
      linearAssetsConfig : linearAssetSpecs,
      experimentalAssetsConfig : experimentalLinearAssetSpecs,
      pointAssetsConfig : pointAssetSpecs,
      groupedPointAssetSpecs: groupedPointAssetSpecs,
      assetGroups: assetGroups
    };
  };
})(this);