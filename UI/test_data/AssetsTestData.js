(function(root) {
  root.AssetsTestData = {
    generate: function() {
      return this.withValidityPeriods(['future', 'current']);
    },
    generateAsset: function(properties) {
      return _.defaults(properties,
        {
          id: 300348,
          externalId: 300066,
          assetTypeId: 10,
          lon: 374750,
          lat: 6677409,
          roadLinkId: 5809,
          imageIds: ['2_1403010580826'],
          bearing: 219,
          validityDirection: 3,
          readOnly: true,
          municipalityNumber: 235,
          validityPeriod: 'current'
        }
      );
    },
    withValidityPeriods: function(validityPeriods) {
      return [this.generateAsset({id: 300348, externalId: 300066, validityPeriod: validityPeriods[0]}),
              this.generateAsset({id: 300347, externalId: 300065, validityPeriod: validityPeriods[1]})];
    }
  };
}(this));
