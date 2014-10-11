require.config({
  paths: {
    jquery:                           '../../bower_components/jquery/dist/jquery.min',
    chai:                             '../../bower_components/chai/chai',
    'chai-jquery':                    '../../bower_components/chai-jquery/chai-jquery',
    eventbus:                         '../src/utils/eventbus',
    AssetsTestData:                   '../test_data/AssetsTestData',
    RoadLinkTestData:                 '../test_data/RoadLinkTestData',
    UserRolesTestData:                '../test_data/UserRolesTestData',
    EnumeratedPropertyValuesTestData: '../test_data/EnumeratedPropertyValuesTestData',
    AssetPropertyNamesTestData:       '../test_data/AssetPropertyNamesTestData',
    SpeedLimitsTestData:              '../test_data/SpeedLimitsTestData',
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
    'AssetTypePropertiesTestData': { exports: 'AssetTypePropertiesTestData' }
  },
  waitSeconds: 10
});
require(['chai',
         'chai-jquery',
         'TestHelpers',
         'GroupingByValidityPeriodSpec',
         'AssetCreationSpec',
         'AssetMoveSpec',
         'SpeedLimitVisualizationSpec',
         'RegroupingAssetsSpec',
         'GroupingInCreationSpec',
         'SpeedLimitSelectionSpec',
         'SpeedLimitSplitSpec'],
        function(chai, chaiJquery, testHelpers) {
  chai.use(chaiJquery);

  eventbus.once('map:initialized', function() {
    if (window.mochaPhantomJS) { mochaPhantomJS.run(); }
    else { mocha.run(); }
  });

  jQuery.browser = {msie: false}; // Fixes broken oskari ie browser test
  Application.start(testHelpers.defaultBackend(), false);
});

