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
      laneModellingTool: 440
    };

    var assetGroups = {
      trWeightGroup: [assetType.trWeightLimits, assetType.trTrailerTruckWeightLimits, assetType.trAxleWeightLimits, assetType.trBogieWeightLimits]
    };

    var saveConditionWithSuggested = function(selectedAsset, authorizationPolicy) {
      var selected = selectedAsset.get();
      return !(selected.isSuggested && authorizationPolicy.isMunicipalityMaintainer()) || authorizationPolicy.isOperator();
    };

    var dateExtract = function (date) {
      return new Date(_.head(date.values).value.replace(/(\d+).(\d+).(\d{4})/, "$2/$1/$3"));
    };

    var datePeriodValueExtract = function (date) {
      var datePeriodValue = date.getPropertyValue().values;
      var startDate = new Date(_.head(datePeriodValue).value.startDate.replace(/(\d+).(\d+).(\d{4})/, "$2/$1/$3"));
      var endDate = new Date(_.head(datePeriodValue).value.endDate.replace(/(\d+).(\d+).(\d{4})/, "$2/$1/$3"));

      return {startDate: startDate, endDate: endDate};
    };

    var isValidPeriodDate = function (startDate, endDate) {
      return startDate <= endDate;
    };

    var isEndDateAfterStartdate = function (date) {
      var datePeriods = datePeriodValueExtract(date);
      return datePeriods.startDate <= datePeriods.endDate;
    };

    var showSuggestBox = function (authorizationPolicy, selectedLinearAsset, value, layerMode) {
      return authorizationPolicy.handleSuggestedAsset(selectedLinearAsset, value, layerMode);
    };

    var isSuggestBoxUnset = function (selectedLinearAsset) {
      return _.some(selectedLinearAsset.get(), function (asset) {return asset.id;});
    };

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
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
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
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
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
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
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
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", weight: 3, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
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
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
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
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
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
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
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
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", weight: 1, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
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

          var isValidPeriodDate =  _.every(datePeriodField, function(date) {
            return date.hasValue() && isInDatePeriod(date) && isEndDateAfterStartdate(date);
          });

          return isValidPeriodDate && isValidIntervalDate;
        },
        form: new DynamicAssetForm ( {
          fields : [
            { publicId: 'kelirikko', label: 'rajoitus', type: 'number', weight: 1, unit: 'kg'},
            { publicId: 'spring_thaw_period', label: 'Kelirikkokausi', type: 'date_period', multiElement: true, weight: 2},
            { publicId: "annual_repetition", label: 'Vuosittain toistuva', type: 'checkbox', values: [{id: 0, label: 'Ei toistu'}, {id: 1, label: 'Jokavuotinen'}], defaultValue: 0, weight: 3},
            { publicId: "suggest_box", label: "vihjetieto", type: 'checkbox',  weight: 4, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
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
            {label: "vihjetieto", type: 'checkbox', publicId: "suggest_box", weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
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
              {label: "vihjetieto", type: 'checkbox', defaultValue: "0", publicId: "suggest_box", weight: 2, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
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
                                {hidden: true, id: 1, label: '(IsE) Liukkaudentorjunta ilman toimenpideaikaa'},
                                {hidden: true, id: 2, label: '(Is) Normaalisti aina paljaana'},
                                {hidden: true, id: 3, label: '(I) Normaalisti paljaana'},
                                {hidden: true, id: 4, label: '(Ib) Pääosin suolattava, ajoittain hieman liukas'},
                                {hidden: true, id: 5, label: '(Ic) Pääosin hiekoitettava, ohut lumipolanne sallittu'},
                                {hidden: true, id: 6, label: '(II) Pääosin lumipintainen'},
                                {hidden: true, id: 7, label: '(III) Pääosin lumipintainen, pisin toimenpideaika'},
                                {hidden: true, id: 8, label: '(L) Kevyen liikenteen laatukäytävät'},
                                {hidden: true, id: 9, label: '(K1) Melko vilkkaat kevyen liikenteen väylät'},
                                {hidden: true, id: 10, label: '(K2) Kevyen liikenteen väylien perus talvihoitotaso'},
                                {hidden: true, id: 11, label: '(ei talvih.) Kevyen liikenteen väylät, joilla ei talvihoitoa'},
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
	          {label: "Vihjetieto", type: 'checkbox', publicId: "suggest_box", values: [{id: 0, label: 'Tarkistettu'}, {id: 1, label: 'Vihjetieto'}], weight: 3, showAndHide: showSuggestBox, isUnSet: isSuggestBoxUnset}
          ]
        }),
        isMultipleLinkSelectionAllowed: true,
        saveCondition: function (fields) {
          var datePeriodField = _.filter(fields, function(field) { return field.getPropertyValue().propertyType === 'date_period'; });

          return _.every(datePeriodField, function (date) {
            return date.hasValue() ? isEndDateAfterStartdate(date) : true;
          });
        },
        hasMunicipalityValidation: false
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
        typeId: assetType.laneModellingTool,
        singleElementEventCategory: 'laneModellingTool',
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
            var dateFields = _.filter(fields, function (field) {
              return field.propertyType === 'date';
            });
            var isValidDate = true;

            if (dateFields.length == 2) {
            var startDate = _.find(dateFields, function (field) {
              return field.publicId === 'start_date';
            });

            var endDate = _.find(dateFields, function (field) {
              return field.publicId === 'end_date';
            });

            if (!_.isEmpty(startDate.values) && !_.isEmpty(endDate.values) && !_.isUndefined(startDate.values[0]) && !_.isUndefined(endDate.values[0]))
              isValidDate = isValidPeriodDate(dateExtract(startDate), dateExtract(endDate));
          }

            var isValidRoadAddresses = true;

            var roadAddressesFields = _.filter(fields, function (field) {
              return field.propertyType === 'number';
            });

              if (roadAddressesFields.length >= 1) {
                var endRoadPartNumber = _.find(roadAddressesFields, function (field) {
                  return field.publicId === 'end_road_part_number';
                });

                var endDistance = _.find(roadAddressesFields, function (field) {
                  return field.publicId === 'end_distance';
                });

                if (_.isUndefined(endRoadPartNumber) || _.isUndefined(endDistance) || _.isEmpty(endRoadPartNumber.values) || _.isEmpty(endDistance.values) || _.isUndefined(endRoadPartNumber.values[0]) || _.isUndefined(endDistance.values[0])){
                  isValidRoadAddresses = false;
                }
              }

              return isValidDate && isValidRoadAddresses;
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
        label: new LaneModellingLabel(),
        hasMunicipalityValidation: true
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
        saveCondition: function(selectedAsset, authorizationPolicy) {
          var suggestedBoxValue = !!parseInt(_.find(selectedAsset.get().propertyData, function(asset) { return asset.publicId === "suggest_box"; }).values[0].propertyValue);
          var suggestedAssetCondition = !(suggestedBoxValue && authorizationPolicy.isMunicipalityMaintainer()) || authorizationPolicy.isOperator();
          return !(suggestedAssetCondition && authorizationPolicy.isMunicipalityMaintainer()) || authorizationPolicy.isOperator();
        },
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
          {symbolUrl: 'images/service_points/chargingPointElectricCarsLeftMenu.png', label: 'Sähköautojen latauspiste', cssClass: 'charging-point'}

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
          var selected = selectedAsset.get();
          return selected.services.length > 0 && (authorizationPolicy.isMunicipalityMaintainer() || authorizationPolicy.isOperator());
        },
        isSuggestedAsset: true,
        hasMunicipalityValidation: true,
        showRoadLinkInfo: true
      },
      {
        typeId: assetType.trafficLights,
        layerName: 'trafficLights',
        title: 'Liikennevalo',
        allowComplementaryLinks: true,
        newAsset: { propertyData: [
            {'name': "Vihjetieto", 'propertyType': 'checkbox', 'publicId': "suggest_box", values: [ {propertyValue: 0} ]}
        ]},
        isSuggestedAsset: true,
        legendValues: [
          {symbolUrl: 'images/point-assets/point_blue.svg', label: 'Liikennevalo'},
          {symbolUrl: 'images/point-assets/point_red.svg', label: 'Geometrian ulkopuolella'}
        ],
        formLabels: {
          singleFloatingAssetLabel: 'liikennevalojen',
          manyFloatingAssetsLabel: 'liikennevalot',
          newAssetLabel: 'liikennevalo'
        },
        hasMunicipalityValidation: true,
        saveCondition: saveConditionWithSuggested,
        authorizationPolicy: new PointAssetAuthorizationPolicy(),
        label: new SuggestionLabel(),
        showRoadLinkInfo: true
      },
      {
        typeId: assetType.trafficSigns,
        layerName: 'trafficSigns',
        title: 'Liikennemerkit',
        allowComplementaryLinks: true,
        newAsset: { validityDirection: 2, propertyData: [
          {'name': 'Liikenteenvastainen', 'propertyType': 'single_choice', 'publicId': "opposite_side_sign", values: [] },
          {'name': 'Tyyppi', 'propertyType': 'single_choice', 'publicId': "trafficSigns_type", values: [ {propertyValue: 1} ] },
          {'name': "Arvo", 'propertyType': 'text', 'publicId': "trafficSigns_value", values: []},
          {'name': "Lisatieto", 'propertyType': 'text', 'publicId': "trafficSigns_info", values: []},
          {'name': "Lisäkilpi", 'propertyType': 'additional_panel_type', 'publicId': "additional_panel", values: [], defaultValue: {panelType:53, panelInfo : "", panelValue : "", formPosition : ""}},
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

          var opposite_side_sign =  _.find( selectedAsset.get().propertyData, function(prop) { if (prop.publicId === "opposite_side_sign") return prop; });
          if (_.isUndefined(opposite_side_sign) || _.isUndefined(opposite_side_sign.values[0]) || opposite_side_sign.values[0].propertyValue === "") {
            selectedAsset.setPropertyByPublicId('opposite_side_sign', '0');
          }

          var functionFn = _.find(validations, function(validation){ return _.includes(validation.types, parseInt(Property.getPropertyValue('Tyyppi', selectedAsset.get())));});
          var suggestedBoxValue = !!parseInt(_.find(selectedAsset.get().propertyData, function(asset) { return asset.publicId === "suggest_box"; }).values[0].propertyValue);
          var suggestedAssetCondition = !(suggestedBoxValue && authorizationPolicy.isMunicipalityMaintainer()) || authorizationPolicy.isOperator();
          return (functionFn ?  functionFn.validate(Property.getPropertyValue('Arvo', selectedAsset.get())) : true) && suggestedAssetCondition;
        },
        readOnlyLayer: TrafficSignReadOnlyLayer,
        showRoadLinkInfo: true
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