(function (root) {

    root.LinkStatus = {
        NotHandled:                 {value: 0, action: "NotHandled"},
        Unchanged:                  {value: 1, action: "Unchanged"},
        New:                        {value: 2, action: "New"},
        Transfer:                   {value: 3, action: "Transfer"},
        Numbering:                  {value: 4, action: "Numbering"},
        Terminated:                 {value: 5, action: "Terminated"},
        Revert:                     {value: 6, action: "Revert"}
    };

    root.Anomaly = {
        None:                       {value: 0, action: "None"},
        NoAddressGiven:             {value: 1, action: "NoAddressGiven"},
        GeometryChanged:            {value: 2, action: "GeometryChanged"},
        Illogical:                  {value: 3, action: "Illogical"}
    };

    root.LinkGeomSource = {
        NormalLinkInterface:        {value: 1, action: "NormalLinkInterface"},
        ComplimentaryLinkInterface: {value: 2, action: "ComplimentaryLinkInterface"},
        SuravageLinkInterface:      {value: 3, action: "SuravageLinkInterface"},
        FrozenLinkInterface:        {value: 4, action: "FrozenLinkInterface"},
        HistoryLinkInterface:       {value: 5, action: "HistoryLinkInterface"},
        Unknown:                    {value: 99, action: "Unknown"}
    };

    root.RoadLinkType = {
        UnknownRoadLinkType:        {value: 0, action: "UnknownRoadLinkType"},
        NormalRoadLinkType:         {value: 1, action: "NormalRoadLinkType"},
        ComplementaryRoadLinkType:  {value: 3, action: "ComplementaryRoadLinkType"},
        FloatingRoadLinkType:       {value: -1, action: "FloatingRoadLinkType"},
        SuravageRoadLink:           {value: 4, action: "SuravageRoadLink"}
    };

    root.ConstructionType = {
        InUse:                      {value: 0, action: "InUse"},
        UnderConstruction:          {value: 1, action: "UnderConstruction"},
        Planned:                    {value: 3, action: "Planned"},
        UnknownConstructionType:    {value: 99, action: "UnknownConstructionType"}
    };

    root.RoadClass = {
        HighwayClass:               {value: 1, action: "HighwayClass"},
        MainRoadClass:              {value: 2, action: "MainRoadClass"},
        RegionalClass:              {value: 3, action: "RegionalClass"},
        ConnectingClass:            {value: 4, action: "ConnectingClass"},
        MinorConnectingClass:       {value: 5, action: "MinorConnectingClass"},
        StreetClass:                {value: 6, action: "StreetClass"},
        RampsAndRoundAboutsClass:   {value: 7, action: "RampsAndRoundAboutsClass"},
        PedestrianAndBicyclesClass: {value: 8, action: "PedestrianAndBicyclesClass"},
        WinterRoadsClass:           {value: 9, action: "WinterRoadsClass"},
        PathsClass:                 {value: 10, action: "PathsClass"},
        NoClass:                    {value: 99, action: "NoClass"}
    };

    root.TrafficDirection = {
        BothDirections:             {value: 2, action: "BothDirections"},
        AgainstDigitizing:          {value: 3, action: "AgainstDigitizing"},
        TowardsDigitizing:          {value: 4, action: "TowardsDigitizing"},
        UnknownDirection:           {value: 99, action: "UnknownDirection"}
    };

    root.SideCode = {
        BothDirections:             {value: 1, action: "BothDirections"},
        TowardsDigitizing:          {value: 2, action: "TowardsDigitizing"},
        AgainstDigitizing:          {value: 3, action: "AgainstDigitizing"},
        Unknown:                    {value: 99, action: "Unknown"}
    };

})(window.LinkValues = window.LinkValues || {});

