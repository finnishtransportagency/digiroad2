(function (root) {
  root.Enumerations = function () {

    this.linkTypes = {
      Motorway: {value: 1, text: 'Moottoritie'},
      Freeway: {value: 4, text: 'Moottoriliikennetie'},
      SingleCarriageway: {value: 3, text: 'Yksiajoratainen tie'},
      MultipleCarriageway: {value: 2, text: 'Moniajoratainen tie'},
      SlipRoad: {value: 6, text: 'Ramppi'},
      Roundabout: {value: 5, text: 'Kiertoliittymä'},
      RestArea: {value: 7, text: 'Levähdysalue', specialLegendRendering: true},
      PedestrianZone: {value: 9, text: 'Jalankulkualue'},
      CycleOrPedestrianPath: {value: 8, text: 'Kävelyn ja pyöräilyn väylä'},
      ServiceOrEmergencyRoad: {value: 10, text: 'Huolto- tai pelastustie', specialLegendRendering: true},
      EnclosedTrafficArea: {value: 11, text: 'Liitännäisliikennealue', specialLegendRendering: true},
      TractorRoad: {value: 12, text: 'Ajopolku'},
      ServiceAccess: {value: 13, text: 'Huoltoaukko'},
      SpecialTransportWithoutGate: {value: 14, text: 'Erikoiskuljetusyhteys ilman puomia'},
      SpecialTransportWithGate: {value: 15, text: 'Erikoiskuljetusyhteys puomilla'},
      CableFerry: {value: 21, text: 'Lautta/lossi'},
      BidirectionalLaneCarriageWay: {value: 22, text: 'Kaksisuuntainen yksikaistainen tie'}
    };

    this.constructionTypes = {
      Planned: {value: 1, text: 'Suunnitteilla', visibleInLegend: true, legendText: 'Suunnitteilla' },
      UnderConstruction: {value: 2, text: 'Rakenteilla', visibleInLegend: true, legendText: 'Rakenteilla'},
      InUse: {value: 3, text: 'Käytössä', visibleInLegend: false, legendText: 'Käytössä'},
      TemporarilyOutOfUse: {value: 4, text: 'Väliaikaisesti poissa käytöstä',
        visibleInLegend: true, legendText: 'Väliaikaisesti poissa käytöstä (haalennettu linkki)'}
    };

    this.administrativeClasses = {
      State: {value: 1, stringValue: 'State', text: 'Valtion omistama', visibleInForm: true},
      Municipality: {value: 2, stringValue: 'Municipality', text:'Kunnan omistama', visibleInForm: true},
      Private: {value: 3, stringValue: 'Private', text: 'Yksityisen omistama', visibleInForm: true}
    };

    this.trafficDirections = {
      BothDirections: {value: 2, stringValue: 'BothDirections', text: 'Molempiin suuntiin'},
      AgainstDigitizing: {value: 3, stringValue: 'AgainstDigitizing', text: 'Digitointisuuntaa vastaan'},
      TowardsDigitizing: {value: 4, stringValue: 'TowardsDigitizing', text: 'Digitointisuuntaan'}
    };
  };
})(this);