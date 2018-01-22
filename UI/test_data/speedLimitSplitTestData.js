(function(root) {
  var generateSpeedLimitLinks = function() {
    return [
      [{
        "id": 111,
        "linkId": 555,
        "value": 40,
        "sideCode": 1,
        "startMeasure": 0,
        "linkSource": 1,
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
        "id": 112,
        "linkId": 666,
        "value": 40,
        "sideCode": 1,
        "startMeasure": 10,
        "linkSource": 1,
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
        "linkId": 777,
        "value": 40,
        "sideCode": 1,
        "startMeasure": 0,
        "linkSource": 1,
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

  root.SpeedLimitSplitTestData = {
    generateSpeedLimitLinks: generateSpeedLimitLinks
  };
})(this);
