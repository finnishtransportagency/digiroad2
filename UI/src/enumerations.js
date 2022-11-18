(function (root) {
    root.Enumerations = function () {
        this.linkTypes = {
            Motorway: {value: 1, text: 'Moottoritie'},
            MultipleCarriageway: {value: 2, text: 'Moniajoratainen tie'},
            SingleCarriageway: {value: 3, text: 'Yksiajoratainen tie'},
            Freeway: {value: 4, text: 'Moottoriliikennetie'},
            Roundabout: {value: 5, text: 'Kiertoliittymä'},
            SlipRoad: {value: 6, text: 'Ramppi'},
            RestArea: {value: 7, text: 'Levähdysalue'},
            CycleOrPedestrianPath: {value: 8, text: 'Kävelyn ja pyöräilyn väylä'},
            PedestrianZone: {value: 9, text: 'Jalankulkualue'},
            ServiceOrEmergencyRoad: {value: 10, text: 'Huolto- tai pelastustie'},
            EnclosedTrafficArea: {value: 11, text: 'Liitännäisliikennealue'},
            TractorRoad: {value: 12, text: 'Ajopolku'},
            ServiceAccess: {value: 13, text: 'Huoltoaukko'},
            SpecialTransportWithoutGate: {value: 14, text: 'Erikoiskuljetusyhteys ilman puomia'},
            SpecialTransportWithGate: {value: 15, text: 'Erikoiskuljetusyhteys puomilla'},
            HardShoulder: {value: 16, text: "#"},
            CableFerry: {value: 21, text: 'Lautta/lossi'},
            BidirectionalLaneCarriageWay: {value: 22, text: 'Kaksisuuntainen yksikaistainen tie'},
            Unknown: {value: 99, text: 'Tuntematon'}
        };

        this.constructionTypes = {
            Planned: {value: 1, text: 'Suunnitteilla' },
            UnderConstruction: {value: 2, text: 'Rakenteilla' },
            InUse: {value: 3, text: 'Käytössä'},
            TemporarilyOutOfUse: {value: 4, text: 'Väliaikaisesti poissa käytöstä (haalennettu linkki)' }
        };


    }
})(this);