/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;

  var roadLinkData =
    [
      [{
        "linkId": 1,
        "administrativeClass": "Private",
        "trafficDirection": "BothDirections",
        "functionalClass": 5,
        "linkType": 3,
        "municipalityCode": 235,
        "verticalLevel": -1,
        "points": [{
          "x": 200.0,
          "y": 200.0
        }, {
          "x": 300.0,
          "y": 200.0
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
        testHelpers.massSelect(openLayersMap,200,200,300,300);
      });
      it('does not show mass update dialog', function() {
        expect($('.mass-update-modal')).not.to.exist;
      });
    });

    describe('and entering edit mode', function() {
      before(function() {
        testHelpers.clickVisibleEditModeButton();
      });

      describe('and performing mass selection', function() {
        before(function() {
          testHelpers.massSelect(openLayersMap,200,200,300,300);
        });
        it('shows mass update dialog', function() {
          expect($('.mass-update-modal')).to.exist;
        });
        after(function() {
          $('.mass-update-modal .btn.btn-secondary.close').click();
        });
      });
    });

    describe('and selecting speed limit layer', function() {
      before(function() {
        testHelpers.selectLayer('speedLimit');
      });

      describe('and performing mass selection', function() {
        before(function() {
          testHelpers.massSelect(openLayersMap,200,200,300,300);
        });
        it('does not show mass update dialog', function() {
          expect($('.mass-update-modal')).not.to.exist;
        });
      });
    });
  });
});
