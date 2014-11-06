(function(root) {
  var generateSpeedLimitLinks = function() {
    var data = [
      {
        "id": 1123812,
        "roadLinkId": 5540,
        "limit": 40,
        "sideCode": 1,
        "position": 0,
        "towardsLinkChain": true,
        "points": [
          {
            "x": 0.0,
            "y": 0.0
          },
          {
            "x": 0.0,
            "y": 100.0
          }
        ]
      }
    ];
    return data;
  };

  var generateRoadLinks = function() {
    return [
      {
        "roadLinkId": 5540,
        "type": "PrivateRoad",
        "points": [
          {
            "x": 0.0,
            "y": 0.0
          },
          {
            "x": 0.0,
            "y": 200.0
          }
        ]
      }
    ];
  };


  root.SpeedLimitSplitTestData = {
    generateSpeedLimitLinks: generateSpeedLimitLinks,
    generateRoadLinks: generateRoadLinks
  };
})(this);