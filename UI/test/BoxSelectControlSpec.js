/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  // 1. Select link property layer *
  // 2. Assert: box control deactivated *
  // 3. Select edit mode
  // 4. Assert: box control activated
  // 5. Select speed limit layer
  // 6. Assert: box control deactivated

  var roadLinkData =
    [
      [{
        "mmlId": 1,
        "administrativeClass": "Private",
        "trafficDirection": "BothDirections",
        "linkType": 2,
        "points": [{
          "x": 90.0,
          "y": 90.0
        }, {
          "x": 100.0,
          "y": 100.0
        }]
      }]
    ];

  describe('when loading application in link property layer', function() {
    var openLayersMap = null;
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        testHelpers.selectLayer('linkProperty');
        done();
      }, testHelpers.defaultBackend()
        .withStartupParameters({ lon: 100.0, lat: 100.0, zoom: 11 })
        .withRoadLinkData(roadLinkData));
    });

    describe('and performing mass selection', function() {
      before(function() {
        testHelpers.massSelect(openLayersMap, 90,90,110,110);
      });
      it('does not show mass update dialog', function() {
        expect($('.mass-update-modal')).not.to.exist;
      });
    });
  });
});
