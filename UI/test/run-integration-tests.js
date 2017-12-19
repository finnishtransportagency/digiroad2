require.config({
  paths: {
    jquery:                           '../../node_modules/jquery/dist/jquery.min',
    chai:                             '../../node_modules/chai/chai',
    'chai-jquery':                    '../../node_modules/chai-jquery/chai-jquery',
    eventbus:                         '../src/utils/eventbus',
    AssetsTestData:                   '../test_data/AssetsTestData',
    RoadLinkTestData:                 '../test_data/RoadLinkTestData',
    UserRolesTestData:                '../test_data/UserRolesTestData',
    EnumeratedPropertyValuesTestData: '../test_data/EnumeratedPropertyValuesTestData',
    AssetPropertyNamesTestData:       '../test_data/AssetPropertyNamesTestData',
    SpeedLimitsTestData:              '../test_data/SpeedLimitsTestData',
    SpeedLimitSplitTestData:          '../test_data/SpeedLimitSplitTestData',
    AssetTypePropertiesTestData:      '../test_data/AssetTypePropertiesTestData'
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
         'TestHelpers',
         'GroupingByValidityPeriodSpec',
         'MassTransitStopCreationSpec',
         'MassTransitStopMoveSpec',
         'SpeedLimitVisualizationSpec',
         'RegroupingMassTransitStopsSpec',
         'GroupingInCreationSpec',
         'SingleSegmentSpeedLimitSpec',
         'SpeedLimitSplitSpec',
         'MultiSegmentSpeedLimitSpec'
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

  Application.start(testHelpers.defaultBackend(), false);
});

