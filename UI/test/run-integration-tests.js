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
    ApplicationSetupTestData:         '../test_data/ApplicationSetupTestData',
    ConfigurationTestData:            '../test_data/ConfigurationTestData',
    AssetPropertyNamesTestData:       '../test_data/AssetPropertyNamesTestData',
    SpeedLimitsTestData:              '../test_data/SpeedLimitsTestData'
  },
  shim: {
    'chai-jquery': ['jquery', 'chai'],
    'eventbus': { exports: 'eventbus' },
    'AssetsTestData': { exports: 'AssetsTestData' },
    'RoadLinkTestData': { exports: 'RoadLinkTestData' },
    'UserRolesTestData': { exports: 'UserRolesTestData' },
    'EnumeratedPropertyValuesTestData': { exports: 'EnumeratedPropertyValuesTestData' },
    'ApplicationSetupTestData': { exports: 'ApplicationSetupTestData' },
    'ConfigurationTestData': { exports: 'ConfigurationTestData' },
    'AssetPropertyNamesTestData': { exports: 'AssetPropertyNamesTestData' },
    'SpeedLimitsTestData': { exports: 'SpeedLimitsTestData' }
  },
  waitSeconds: 10
});
require(['chai',
         'chai-jquery',
         'AssetsTestData',
         'RoadLinkTestData',
         'UserRolesTestData',
         'EnumeratedPropertyValuesTestData',
         'ApplicationSetupTestData',
         'ConfigurationTestData',
         'AssetPropertyNamesTestData',
         'SpeedLimitsTestData',
         'GroupingByValidityPeriodSpec',
         'AssetCreationSpec',
         'AssetMoveSpec',
         'SpeedLimitVisualizationSpec',
         'RegroupingAssetsSpec'],
        function(chai,
                 chaiJquery,
                 AssetsTestData,
                 RoadLinkTestData,
                 UserRolesTestData,
                 EnumeratedPropertyValuesTestData,
                 ApplicationSetupTestData,
                 ConfigurationTestData,
                 AssetPropertyNamesTestData,
                 SpeedLimitsTestData) {
  chai.use(chaiJquery);

  eventbus.once('map:initialized', function() {
    if (window.mochaPhantomJS) { mochaPhantomJS.run(); }
    else { mocha.run(); }
  });

  jQuery.browser = {msie: false}; // Fixes broken oskari ie browser test
  Application.start(
    Backend.withRoadLinkData(RoadLinkTestData.generate())
      .withUserRolesData(UserRolesTestData.generate())
      .withEnumeratedPropertyValues(EnumeratedPropertyValuesTestData.generate())
      .withApplicationSetupData(ApplicationSetupTestData.generate())
      .withConfigurationData(ConfigurationTestData.generate())
      .withAssetPropertyNamesData(AssetPropertyNamesTestData.generate())
      .withAssetsData(AssetsTestData.generate())
      .withSpeedLimitsData(SpeedLimitsTestData.generate()));
});

