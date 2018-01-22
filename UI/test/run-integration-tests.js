require.config({
  paths: {
    jquery:                           '../../node_modules/jquery/dist/jquery.min',
    chai:                             '../../node_modules/chai/chai',
    'chai-jquery':                    '../../node_modules/chai-jquery/chai-jquery',
    eventbus:                         '../src/utils/eventbus',
    AssetsTestData:                   '../test_data/assetsTestData',
    RoadLinkTestData:                 '../test_data/roadLinkTestData',
    UserRolesTestData:                '../test_data/userRolesTestData',
    EnumeratedPropertyValuesTestData: '../test_data/enumeratedPropertyValuesTestData',
    AssetPropertyNamesTestData:       '../test_data/assetPropertyNamesTestData',
    SpeedLimitsTestData:              '../test_data/speedLimitsTestData',
    SpeedLimitSplitTestData:          '../test_data/speedLimitSplitTestData',
    AssetTypePropertiesTestData:      '../test_data/assetTypePropertiesTestData'
  },
  shim: {
    'chai-jquery': ['jquery', 'chai'],
    'eventbus': { exports: 'eventbus' },
    'AssetsTestData': { exports: 'AssetsTestData' },
    'RoadLinkTestData': { exports: 'RoadLinkTestData' },
    'UserRolesTestData': { exports: 'UserRolesTestData' },
    'EnumeratedPropertyValuesTestData': { exports: 'EnumeratedPropertyValuesTestData' },
    'AssetPropertyNamesTestData': { exports: 'AssetPropertyNamesTestData' },
    'SpeedLimitsTestData': { exports: 'SpeedLimitsTestData' },
    'SpeedLimitSplitTestData': { exports: 'SpeedLimitSplitTestData' },
    'AssetTypePropertiesTestData': { exports: 'AssetTypePropertiesTestData' }
  },
  waitSeconds: 10
});
require(['chai',
         'chai-jquery',
         'testHelpers',
         'integration-tests/groupingByValidityPeriodSpec',
         'integration-tests/massTransitStopCreationSpec',
         'integration-tests/massTransitStopMoveSpec',
         'integration-tests/speedLimitVisualizationSpec',
         'integration-tests/regroupingMassTransitStopsSpec',
         'integration-tests/groupingInCreationSpec',
         'integration-tests/singleSegmentSpeedLimitSpec',
         'integration-tests/speedLimitSplitSpec',
         'integration-tests/multiSegmentSpeedLimitSpec'
        ],
        function(chai, chaiJquery, testHelpers) {
  chai.use(chaiJquery);

  //Workaround to give PhantomJS openlayers support
  Function.prototype.bind = Function.prototype.bind || function (thisp) {
    var fn = this;
    return function () {
      return fn.apply(thisp, arguments);
    };
  };
  window.requestAnimationFrame = window.requestAnimationFrame || function(callback){
    window.setTimeout(callback, 1000 / 60);
  };

  eventbus.once('map:initialized', function() {
    if (window.mochaPhantomJS) { mochaPhantomJS.run(); }
    else { mocha.run(); }
  });

  Application.start(testHelpers.defaultBackend(), false, true);
});

