(function(root) {
  var generateSpeedLimitLinks = function() {
    var data = [
      {
        "id": 111,
        "roadLinkId": 555,
        "value": 40,
        "sideCode": 1,
        "position": 0,
        "towardsLinkChain": false,
        "points": [
          {
            "x": 0.0,
            "y": 100.0
          },
          {
            "x": 0.0,
            "y": 0.0
          }
        ]
      },
      {
        "id": 111,
        "roadLinkId": 666,
        "value": 40,
        "sideCode": 1,
        "position": 1,
        "towardsLinkChain": false,
        "points": [
          {
            "x": 0.0,
            "y": 150.0
          },
          {
            "x": 0.0,
            "y": 100.0
          }
        ]
      },
      {
        "id": 111,
        "roadLinkId": 777,
        "value": 40,
        "sideCode": 1,
        "position": 2,
        "towardsLinkChain": true,
        "points": [
          {
            "x": 0.0,
            "y": 200.0
          },
          {
            "x": 0.0,
            "y": 150.0
          }
        ]
      }
    ];
    return data;
  };

  var generateRoadLinks = function() {
    return [
      {
        "roadLinkId": 555,
        "type": "PrivateRoad",
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
      },
      {
        "roadLinkId": 666,
        "type": "PrivateRoad",
        "points": [
          {
            "x": 0.0,
            "y": 100.0
          },
          {
            "x": 0.0,
            "y": 150.0
          }
        ]
      },
      {
        "roadLinkId": 777,
        "type": "PrivateRoad",
        "points": [
          {
            "x": 0.0,
            "y": 150.0
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