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

    this.trafficSignsAllowedOnPedestrianCyclingLinks = [9, 14, 30, 31, 32, 61, 70, 71, 72, 85, 89, 98, 99, 111, 112, 118, 119, 163, 164, 187, 188, 189, 190, 235, 236, 280, 281, 282, 283, 284, 285, 298, 299, 300, 301, 302, 303, 362, 398];

  };
})(this);