(function(root) {
  var generateSpeedLimitLinks = function() {
    return [
      [{
        "id": 111,
        "mmlId": 555,
        "value": 40,
        "sideCode": 1,
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
        "id": 112,
        "mmlId": 666,
        "value": 40,
        "sideCode": 1,
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
        "id": 113,
        "mmlId": 777,
        "value": 40,
        "sideCode": 1,
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
      }]
    ];
  };

  var generateRoadLinks = function() {
    return [
      {
        "mmlId": 555,
        "type": "Private",
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
        "mmlId": 666,
        "type": "Private",
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
        "mmlId": 777,
        "type": "Private",
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
      }];
  };


  root.SpeedLimitSplitTestData = {
    generateSpeedLimitLinks: generateSpeedLimitLinks,
    generateRoadLinks: generateRoadLinks
  };
})(this);