(function(root) {
  root.AssetsTestData = {
    generate: function() {
      return this.withValidityPeriods(['future', 'current']);
    },
    withValidityPeriods: function(validityPeriods) {
      return [
        {
          id: 300348,
          externalId: 300066,
          assetTypeId: 10,
          lon: 374750,
          lat: 6677409,
          roadLinkId: 5771,
          imageIds: [
            "2_1403010580826"
          ],
          bearing: 219,
          validityDirection: 3,
          readOnly: true,
          municipalityNumber: 235,
          validityPeriod: validityPeriods[0]
        },
        {
          id: 300347,
          externalId: 300065,
          assetTypeId: 10,
          lon: 374750,
          lat: 6677409,
          roadLinkId: 5771,
          imageIds: [
            "2_1403010580826"
          ],
          bearing: 219,
          validityDirection: 3,
          readOnly: true,
          municipalityNumber: 235,
          validityPeriod: validityPeriods[1]
        }
      ];
    }
  }
}(this));
