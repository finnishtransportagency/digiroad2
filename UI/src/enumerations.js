(function (root) {
  root.Enumerations = function () {

    this.linkTypes = {
      Motorway: {value: 1, text: 'Moottoritie'},
      Freeway: {value: 4, text: 'Moottoriliikennetie'},
      SingleCarriageway: {value: 3, text: 'Yksiajoratainen tie'},
      MultipleCarriageway: {value: 2, text: 'Moniajoratainen tie'},
      Roundabout: {value: 5, text: 'Kiertoliittymä'},
      SlipRoad: {value: 6, text: 'Ramppi'},
      RestArea: {value: 7, text: 'Levähdysalue', specialLegendRendering: true},
      PedestrianZone: {value: 9, text: 'Jalankulkualue'},
      CycleOrPedestrianPath: {value: 8, text: 'Pyörätie tai yhdistetty pyörätie ja jalkakäytävä'},
      ServiceOrEmergencyRoad: {value: 10, text: 'Huolto- tai pelastustie', specialLegendRendering: true},
      EnclosedTrafficArea: {value: 11, text: 'Liitännäisliikennealue', specialLegendRendering: true},
      TractorRoad: {value: 12, text: 'Ajopolku'},
      ServiceAccess: {value: 13, text: 'Huoltoaukko'},
      SpecialTransportWithoutGate: {value: 14, text: 'Erikoiskuljetusyhteys ilman puomia'},
      SpecialTransportWithGate: {value: 15, text: 'Erikoiskuljetusyhteys puomilla'},
      CableFerry: {value: 21, text: 'Lautta/lossi'},
      BidirectionalLaneCarriageWay: {value: 22, text: 'Kaksisuuntainen yksikaistainen tie'},
      Unknown: {value: 99, text: 'Tuntematon'}
    };

    this.functionalClasses = {
      1: {value: 1, text: 'Luokka 1'},
      2: {value: 2, text: 'Luokka 2'},
      3: {value: 3, text: 'Luokka 3'},
      4: {value: 4, text: 'Luokka 4'},
      5: {value: 5, text: 'Luokka 5'},
      6: {value: 6, text: 'Luokka 6: Muu yksityistie'},
      7: {value: 7, text: 'Luokka 7: Ajopolku'},
      8: {value: 8, text: 'Luokka 8: Kävelyn ja pyöräilyn väylä'},
      9: {value: 9, text: 'Luokka 9'}
    };

    this.twoWayLaneLinkTypes = [
      this.linkTypes.SpecialTransportWithoutGate,
      this.linkTypes.SpecialTransportWithGate,
      this.linkTypes.ServiceAccess,
      this.linkTypes.CycleOrPedestrianPath,
      this.linkTypes.BidirectionalLaneCarriageWay,
      this.linkTypes.RestArea
  ];

    this.constructionTypes = {
      Planned: {value: 1, text: 'Suunnitteilla', visibleInLegend: true, legendText: 'Suunnitteilla' },
      UnderConstruction: {value: 2, text: 'Rakenteilla', visibleInLegend: true, legendText: 'Rakenteilla'},
      InUse: {value: 3, text: 'Käytössä', visibleInLegend: false, legendText: 'Käytössä'},
      TemporarilyOutOfUse: {value: 4, text: 'Väliaikaisesti poissa käytöstä',
        visibleInLegend: true, legendText: 'Väliaikaisesti poissa käytöstä (haalennettu linkki)'}
    };

    this.administrativeClasses = {
      State: {value: 1, stringValue: 'State', text: 'Valtion omistama', visibleInForm: true},
      Municipality: {value: 2, stringValue: 'Municipality', text: 'Kunnan omistama', visibleInForm: true},
      Private: {value: 3, stringValue: 'Private', text: 'Yksityisen omistama', visibleInForm: true},
      Unknown: {value: 99, stringValue: 'Unknown', text: 'Ei tiedossa', visibleInForm: false}
    };

    this.trafficDirections = {
      BothDirections: {value: 2, stringValue: 'BothDirections', text: 'Molempiin suuntiin'},
      AgainstDigitizing: {value: 3, stringValue: 'AgainstDigitizing', text: 'Digitointisuuntaa vastaan'},
      TowardsDigitizing: {value: 4, stringValue: 'TowardsDigitizing', text: 'Digitointisuuntaan'}
    };

    this.assetTypes = {
      MassTransitStopAsset: { typeId: 10, geometryType: 'point', label: 'MassTransitStop', layerName: 'massTransitStop', nameFI: 'Bussipysäkit' },
      SpeedLimitAsset: { typeId: 20, geometryType: 'linear', label: 'SpeedLimit', layerName: 'speedLimits', nameFI: 'Nopeusrajoitukset' },
      TotalWeightLimit: { typeId: 30, geometryType: 'linear', label: 'TotalWeightLimit', layerName: 'totalWeightLimit', nameFI: 'Kokonaispainorajoitukset' },
      TrailerTruckWeightLimit: { typeId: 40, geometryType: 'linear', label: 'TrailerTruckWeightLimit', layerName: 'trailerTruckWeightLimit', nameFI: 'Ajoneuvoyhdistelmän suurin sallittu massa' },
      AxleWeightLimit: { typeId: 50, geometryType: 'linear', label: 'AxleWeightLimit', layerName: 'axleWeightLimit', nameFI: 'Ajoneuvon suurin sallittu akselimassa' },
      BogieWeightLimit: { typeId: 60, geometryType: 'linear', label: 'BogieWeightLimit', layerName: 'bogieWeightLimit', nameFI: 'Ajoneuvon suurin sallittu telimassa' },
      HeightLimit: { typeId: 70, geometryType: 'linear', label: 'HeightLimit', layerName: 'heightLimit', nameFI: 'Ajoneuvon suurin sallittu korkeus' },
      LengthLimit: { typeId: 80, geometryType: 'linear', label: 'LengthLimit', layerName: 'lengthLimit', nameFI: 'Ajoneuvon tai -yhdistelmän suurin sallittu pituus' },
      WidthLimit: { typeId: 90, geometryType: 'linear', label: 'WidthLimit', layerName: 'widthLimit', nameFI: 'Ajoneuvon suurin sallittu leveys' },
      LitRoad: { typeId: 100, geometryType: 'linear', label: 'LitRoad', layerName: 'litRoad', nameFI: 'Valaistu tie' },
      PavedRoad: { typeId: 110, geometryType: 'linear', label: 'PavedRoad', layerName: 'pavedRoad', nameFI: 'Päällystetty tie' },
      RoadWidth: { typeId: 120, geometryType: 'linear', label: 'RoadWidth', layerName: 'roadWidth', nameFI: 'Tien leveys' },
      DamagedByThaw: { typeId: 130, geometryType: 'linear', label: 'DamagedByThaw', layerName: 'roadDamagedByThaw', nameFI: 'Kelirikko' },
      NumberOfLanes: { typeId: 140, geometryType: 'linear', label: 'NumberOfLanes', layerName: 'numberOfLanes', nameFI: 'Kaistojen lukumäärä' },
      MassTransitLane: { typeId: 160, geometryType: 'linear', label: 'MassTransitLane', layerName: 'massTransitLanes', nameFI: 'Joukkoliikennekaistat' },
      TrafficVolume: { typeId: 170, geometryType: 'linear', label: 'TrafficVolume', layerName: 'trafficVolume', nameFI: 'Liikennemäärä' },
      WinterSpeedLimit: { typeId: 180, geometryType: 'linear', label: 'WinterSpeedLimit', layerName: 'winterSpeedLimits', nameFI: 'Talvinopeusrajoitukset' },
      Prohibition: { typeId: 190, geometryType: 'linear', label: 'Prohibition', layerName: 'prohibition', nameFI: 'Ajokielto' },
      PedestrianCrossings: { typeId: 200, geometryType: 'point', label: 'PedestrianCrossings', layerName: 'pedestrianCrossings', nameFI: 'Suojatie' },
      HazmatTransportProhibition: { typeId: 210, geometryType: 'linear', label: 'HazmatTransportProhibition', layerName: 'hazardousMaterialTransportProhibition', nameFI: 'VAK-rajoitus' },
      Obstacles: { typeId: 220, geometryType: 'point', label: 'Obstacles', layerName: 'obstacles', nameFI: 'Esterakennelma' },
      RailwayCrossings: { typeId: 230, geometryType: 'point', label: 'RailwayCrossings', layerName: 'railwayCrossings', nameFI: 'Rautatien tasoristeys' },
      DirectionalTrafficSigns: { typeId: 240, geometryType: 'point', label: 'DirectionalTrafficSigns', layerName: 'directionalTrafficSigns', nameFI: 'Opastustaulu ja sen informaatio' },
      ServicePoints: { typeId: 250, geometryType: 'point', label: 'ServicePoints', layerName: 'servicePoints', nameFI: 'Palvelupiste' },
      EuropeanRoads: { typeId: 260, geometryType: 'linear', label: 'EuropeanRoads', layerName: 'europeanRoads', nameFI: 'Eurooppatienumero' },
      ExitNumbers: { typeId: 270, geometryType: 'linear', label: 'ExitNumbers', layerName: 'exitNumbers', nameFI: 'Liittymänumero' },
      TrafficLights: { typeId: 280, geometryType: 'point', label: 'TrafficLights', layerName: 'trafficLights', nameFI: 'Liikennevalo' },
      MaintenanceRoadAsset: { typeId: 290, geometryType: 'linear', label: 'MaintenanceRoads', layerName: 'maintenanceRoads', nameFI: 'Huoltotie' },
      TrafficSigns: { typeId: 300, geometryType: 'point', label: 'TrafficSigns', layerName: 'trafficSigns', nameFI: 'Liikennemerkki' },
      UnknownAssetTypeId: { typeId: 99, geometryType: '', label: '', layerName: '', nameFI: '' },
      Manoeuvres: { typeId: 380, geometryType: 'linear', label: 'Manoeuvre', layerName: 'manoeuvre', nameFI: 'Kääntymisrajoitus' },
      CareClass: { typeId: 390, geometryType: 'linear', label: 'CareClass', layerName: 'careClass', nameFI: 'Hoitoluokat' },
      CarryingCapacity: { typeId: 400, geometryType: 'linear', label: 'CarryingCapacity', layerName: 'carryingCapacity', nameFI: 'Kantavuus' },
      RoadWorksAsset: { typeId: 420, geometryType: 'linear', label: 'RoadWorks', layerName: 'roadWorks', nameFI: 'Tietyot' },
      ParkingProhibition: { typeId: 430, geometryType: 'linear', label: 'ParkingProhibition', layerName: 'parkingProhibition', nameFI: 'Pysäköintikielto' },
      CyclingAndWalking: { typeId: 440, geometryType: 'linear', label: 'CyclingAndWalking', layerName: 'cyclingAndWalking', nameFI: 'Käpy tietolaji' },
      Lanes: { typeId: 450, geometryType: 'linear', label: 'Lanes', layerName: 'lanes', nameFI: 'Kaista' },
      RoadLinkProperties: { typeId: 460, geometryType: 'linear', label: 'RoadLinkProperties', layerName: 'roadlink', nameFI: 'Tielinkin ominaisuustiedot' }
    };

    this.trafficSignValues = {
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

    this.additionalValues = {
      additionalPanels: { values : [45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 361, 362, 363],
        groupName: 'Lisakilvet', groupIndicative: 'H'}
    };

    this.trafficSignsAllowedOnPedestrianCyclingLinks = [7, 9, 13, 14, 22, 23, 26, 27, 30, 31, 32, 44, 61, 68, 70, 71, 72, 85, 89, 98, 99, 111, 112, 118,
      119, 130, 131, 132, 133, 136, 163, 164, 187, 188, 189, 190, 215, 235, 236, 280, 281, 282, 283, 284, 285, 298, 299, 300, 301, 302, 303, 362, 395, 398];

    this.additionalPanelsAllowedOnPedestrianCyclingLinks = [61, 62, 362, 140, 141, 361];

    this.trafficSignValuesReadOnly = {
      speedLimit: { values : [1, 2, 3, 4, 5, 6]},
      totalWeightLimit: {values : [32, 33, 34, 35]},
      trailerTruckWeightLimit: {values : [32, 33, 34, 35]},
      axleWeightLimit:{values : [32, 33, 34, 35]},
      bogieWeightLimit: {values : [32, 33, 34, 35]},
      heightLimit: {values : [31]},
      lengthLimit: {values : [8]},
      widthLimit: {values : [30]},
      prohibition: {values: [13, 14, 15, 16, 17, 18, 19, 21, 22, 23, 24, 25, 26]},
      parkingProhibition: {values: [100, 101]},
      hazardousMaterialTransportProhibition: {values : [20]},
      manoeuvre: {values: [10, 11, 12]},
      pedestrianCrossings: { values: [7] },
      trafficSigns: {values: [45,46,139,140,141,142,143,144,47,48,49,50,145,51,138,146,147,52,53,54,55,56,57,58,59,60,61,62,148,149,150,151]}, //remove after batch to merge additional panels (1707) is completed. part of experimental feature
      cyclingAndWalking: {values: this.trafficSignsAllowedOnPedestrianCyclingLinks},
      roadWork: { values: [85] } //layername
    };

    this.trafficSignsNoLongerAvailable = [143, 147, 162, 166, 167, 168, 247, 274, 287, 288, 357, 359];

    this.trafficSignsTypeLinearGenerators = [10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 85, 100, 101];

    this.manoeuvreExceptions = [
        {typeId: 21, title: 'Huoltoajo'},
        {typeId: 22, title: 'Tontille ajo'},
        {typeId: 10, title: 'Mopo'},
        {typeId: 9, title: 'Moottoripyörä'},
        {typeId: 27, title: 'Moottorikelkka'},
        {typeId: 5, title: 'Linja-auto'},
        {typeId: 8, title: 'Taksi'},
        {typeId: 7, title: 'Henkilöauto'},
        {typeId: 6, title: 'Pakettiauto'},
        {typeId: 4, title: 'Kuorma-auto'},
        {typeId: 15, title: 'Matkailuajoneuvo'},
        {typeId: 19, title: 'Sotilasajoneuvo'},
        {typeId: 13, title: 'Ajoneuvoyhdistelmä'},
        {typeId: 14, title: 'Traktori tai maatalousajoneuvo'}
      ];

    this.manoeuvreValidityPeriodDays = [
      {value: 1, title: 'Su'},
      {value: 2, title: 'Ma-Pe'},
      {value: 3, title: 'La'},
      {value: 99, title: 'Tuntematon'}
    ];

    this.massTransitStopTypes = {
      Tram:              { value: 1,  text: 'Raitiovaunu' },
      Bus:               { value: 2,  text: 'Bussi' },
      Virtual:           { value: 5,  text: 'Virtuaalipysäkki' },
      Terminal:          { value: 6,  text: 'Terminaali' },
      ServicePoint:      { value: 7,  text: 'Palvelupiste' },
      Unknown:           { value: 99, text: 'Tuntematon' }
    };

  };
})(this);