var parseQueryParameters = function(queryString) {
  return _.chain(queryString.split('&'))
    .map(function(param) {
      return param.split('=');
    })
    .reduce(function(acc, param) {
      acc[param[0]] = param[1];
      return acc;
    }, {})
    .value();
};

$(function() {
  var queryString = window.location.search.substring(1);
  var parameters = parseQueryParameters(queryString);
  var fakeMode = parameters.withFakeData === 'true';

  Analytics.start();

  if (fakeMode) {
    getScripts(['test_data/RoadLinkTestData.js', 'test_data/SpeedLimitsTestData.js'], function() {
      var speedLimitsData = SpeedLimitsTestData.generate();
      Application.start(new Backend()
        .withRoadLinkData(RoadLinkTestData.generate())
        .withSpeedLimitsData(speedLimitsData));
    });
  } else {
    Application.start();
  }
});
