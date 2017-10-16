(function (root) {

    root.LinkStatus = {
        NotHandled:                 {value: 0, description: "NotHandled"},
        Unchanged:                  {value: 1, description: "Unchanged"},
        New:                        {value: 2, description: "New"},
        Transfer:                   {value: 3, description: "Transfer"},
        Numbering:                  {value: 4, description: "Numbering"},
        Terminated:                 {value: 5, description: "Terminated"},
        Revert:                     {value: 6, description: "Revert"}
    };

    root.Anomaly = {
        None:                       {value: 0, description: "None"},
        NoAddressGiven:             {value: 1, description: "NoAddressGiven"},
        GeometryChanged:            {value: 2, description: "GeometryChanged"},
        Illogical:                  {value: 3, description: "Illogical"}
    };

    root.LinkGeomSource = {
        NormalLinkInterface:        {value: 1, description: "NormalLinkInterface"},
        ComplimentaryLinkInterface: {value: 2, description: "ComplimentaryLinkInterface"},
        SuravageLinkInterface:      {value: 3, description: "SuravageLinkInterface"},
        FrozenLinkInterface:        {value: 4, description: "FrozenLinkInterface"},
        HistoryLinkInterface:       {value: 5, description: "HistoryLinkInterface"},
        Unknown:                    {value: 99, description: "Unknown"}
    };

    root.RoadLinkType = {
        UnknownRoadLinkType:        {value: 0, description: "UnknownRoadLinkType"},
        NormalRoadLinkType:         {value: 1, description: "NormalRoadLinkType"},
        ComplementaryRoadLinkType:  {value: 3, description: "ComplementaryRoadLinkType"},
        FloatingRoadLinkType:       {value: -1, description: "FloatingRoadLinkType"},
        SuravageRoadLink:           {value: 4, description: "SuravageRoadLink"}
    };

    root.ConstructionType = {
        InUse:                      {value: 0, description: "InUse"},
        UnderConstruction:          {value: 1, description: "UnderConstruction"},
        Planned:                    {value: 3, description: "Planned"},
        UnknownConstructionType:    {value: 99, description: "UnknownConstructionType"}
    };

    root.RoadClass = {
        HighwayClass:               {value: 1, description: "HighwayClass"},
        MainRoadClass:              {value: 2, description: "MainRoadClass"},
        RegionalClass:              {value: 3, description: "RegionalClass"},
        ConnectingClass:            {value: 4, description: "ConnectingClass"},
        MinorConnectingClass:       {value: 5, description: "MinorConnectingClass"},
        StreetClass:                {value: 6, description: "StreetClass"},
        RampsAndRoundAboutsClass:   {value: 7, description: "RampsAndRoundAboutsClass"},
        PedestrianAndBicyclesClass: {value: 8, description: "PedestrianAndBicyclesClass"},
        WinterRoadsClass:           {value: 9, description: "WinterRoadsClass"},
        PathsClass:                 {value: 10, description: "PathsClass"},
        NoClass:                    {value: 99, description: "NoClass"}
    };

    root.TrafficDirection = {
        BothDirections:             {value: 2, description: "BothDirections"},
        AgainstDigitizing:          {value: 3, description: "AgainstDigitizing"},
        TowardsDigitizing:          {value: 4, description: "TowardsDigitizing"},
        UnknownDirection:           {value: 99, description: "UnknownDirection"}
    };

    root.SideCode = {
        BothDirections:             {value: 1, description: "BothDirections"},
        TowardsDigitizing:          {value: 2, description: "TowardsDigitizing"},
        AgainstDigitizing:          {value: 3, description: "AgainstDigitizing"},
        Unknown:                    {value: 99, description: "Unknown"}
    };

    root.CalibrationCode = {
        None:                       {value: 0, description: "None"},
        AtEnd:                      {value: 1, description: "AtEnd"},
        AtBeginning:                {value: 2, description: "AtBeginning"},
        AtBoth:                     {value: 3, description: "AtBoth"}
    };

})(window.LinkValues = window.LinkValues || {});

