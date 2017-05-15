/*jshint expr: true*/
define(['chai', 'eventbus', 'TestHelpers'], function(chai, eventbus, testHelpers) {
  var expect = chai.expect;

  var floatingsLinkIds = [1718152, 1718151];
  var unknownRoadLinkId = 500130202;

  describe('when loading application', function() {
    this.timeout(1500000);
    var openLayersMap;
    before(function(done) {
      var backend = testHelpers.fakeBackend(13, testHelpers.selectTestData('roadAddress'),354810.0, 6676460.0);
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        testHelpers.clickVisibleEditModeButton();
        done();
      }, backend);
    });

    describe('Selecting the first floating', function() {
      //Do this before

      before(function(done){
        var ol3Feature = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadLayerName(), floatingsLinkIds[Math.round(Math.random())]);
        testHelpers.selectSingleFeature(openLayersMap, ol3Feature);
        done();
      });

      it('check if the form opened for the correct floatings', function() {
        var formLinkIds = $('[id^=VALITUTLINKIT] p');
        expect(formLinkIds.length).to.equals(2);
        var firstLinkId = parseInt($('[id^=VALITUTLINKIT] p').eq(0).html());
        var secondLinkId = parseInt($('[id^=VALITUTLINKIT] p').eq(1).html());
        expect(floatingsLinkIds).to.include.members([firstLinkId, secondLinkId]);
        var isValintaButtonDisabled = $('.link-properties button.continue').is(":disabled");
        expect(isValintaButtonDisabled).to.be.false;
      });
    });

    describe('Clicking the \"Valinta\" button',function(){
      before(function(done) {
        testHelpers.clickValintaButton();
        done();
      });

      it('check that the \"Valinta\" was pressed and the unknowns are \"forward\"', function () {
        var isValintaButtonDisabled = $('.link-properties button.continue').is(":disabled");
        expect(isValintaButtonDisabled).to.be.true;
        var pickFeatures = testHelpers.getFeatures(openLayersMap, 'pickRoadsLayer');
        expect(pickFeatures).to.be.not.empty;
      });
    });

    describe('Selecting a unknown road to transfer the floatings', function(){
      before(function(done){
        var ol3Feature = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getPickRoadsLayerName(), unknownRoadLinkId);
        testHelpers.selectSingleFeature(openLayersMap, ol3Feature);
        done();
      });

      it('Check if the unknown road was selected via form',function(){
        var expectedLinkIds = [1718138, 1718147];
        var adjacentsButtons = $('[id^=sourceButton]');
        expect(adjacentsButtons.length).to.equals(2);
        var sourceALinkId = parseInt($('[id^=sourceButton]').eq(0).val());
        var sourceBLinkId = parseInt($('[id^=sourceButton]').eq(1).val());
        expect(expectedLinkIds).to.include.members([sourceALinkId, sourceBLinkId]);
        var isMoveButtonDisabled = $('.link-properties button.move').is(":disabled");
        expect(isMoveButtonDisabled).to.be.false;
      });

      it('Check if the unknown road was selected via Layers', function(){
        var unknownFeatureFromPickLayer = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getPickRoadsLayerName(),unknownRoadLinkId);
        expect(unknownFeatureFromPickLayer).to.be.undefined;
        var unknownFeatureFromGreenLayer = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getGreenRoadLayerName(),unknownRoadLinkId);
        expect(unknownFeatureFromGreenLayer).to.not.be.undefined;
        expect(unknownFeatureFromGreenLayer.roadLinkData.linkId).to.be.equal(unknownRoadLinkId);
      });
    });

  });
});
