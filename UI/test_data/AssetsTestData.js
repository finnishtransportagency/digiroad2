(function(root) {
  root.AssetsTestData = {
    generate: function() {
      return this.withValidityPeriods(['future', 'current']);
    },
    generateAsset: function(properties) {
      return _.defaults(properties,
        {
          id: 300348,
          nationalId: 300066,
          assetTypeId: 10,
          lon: 374750,
          lat: 6677409,
          roadLinkId: 5809,
          stopTypes: [2],
          bearing: 219,
          linkSource: 1,
          validityDirection: 3,
          readOnly: true,
          municipalityNumber: 235,
          validityPeriod: 'current'
        }
      );
    },
    withValidityPeriods: function(validityPeriods) {
      return [this.generateAsset({id: 300348, nationalId: 300066, validityPeriod: validityPeriods[0]}),
              this.generateAsset({id: 300347, nationalId: 300065, validityPeriod: validityPeriods[1]})];
    }
  };
}(this));
