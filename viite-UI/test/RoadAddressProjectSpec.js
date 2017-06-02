/*jshint expr: true*/
define(['chai', 'eventbus', 'TestHelpers'], function(chai, eventbus, testHelpers) {
// define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  var assert = chai.assert;

  describe('when click on the Tieosoiteprojektit button', function() {
    this.timeout(1500000);
    var openLayersMap;
    before(function(done) {
      var backend = testHelpers.fakeBackend(13, testHelpers.selectTestData('roadAddress'),354810.0, 6676460.0);
      // 6679154I:359436

      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        eventbus.once('roadLayer:featuresLoaded', function() {
          console.log("Started the application.");
          done();
        });
      }, backend);
    });

    //1-first -open project list
    before(function(done) {
      $('[id^=projectListButton]:visible').prop('disabled', false);
      $('[id^=projectListButton]:visible').attr('disabled', false);
      testHelpers.clickProjectListButton();
      console.log("Ended the first test.");
      done();
    });

    it('open project list window', function () {
      $('[id^=projectListButton]').prop('disabled', false);
      $('[id^=projectListButton]').attr('disabled', false);
      assert($('[id^=project-window]:visible').length > 0, "Windows didn't open. Check permissions.");
    });

    //2-second -click Uusi tieosoiteprojekti button and display form info
    describe('when clicking in new project button', function() {
      before(function(done) {
        $('[id*="open-project"]:visible').prop('disabled', false);
        $('[id*="open-project"]:visible').attr('disabled', false);
        testHelpers.clickNewProjectButton();
        console.log("Ended the second test");
        done();
      });

      it('open project form info', function () {
        $('.project-form button.next:visible').prop('disabled', false);
        $('.project-form button.next:visible').attr('disabled', false);
        assert($('.project-form:visible').length > 0, "Form didn't open.");
      });
    });

    // 3-third -click in the reserve button
    describe('when clicking in reserve aka Varaa button', function() {
      before(function (done) {
        $('[id^=nimi]').val('Project Two');
        $('[id^=alkupvm]').val('30.5.2017');
        $('[id^=tie]').val('1130');
        $('[id^=aosa]').val('4');
        $('[id^=losa]').val('4');
        $('.btn-next').prop('disabled', false);
        $('.btn-next').attr('disabled', false);
        $('.btn-reserve').prop('disabled', false);
        $('.btn-reserve').attr('disabled', false);
        eventbus.on('roadPartsValidation:checkRoadParts', function(validationResult){
          if(validationResult.success == "ok"){
            done();
            console.log("Ended the 3rd test");
          }
        });
        testHelpers.clickReserveButton();
      });

      it('Seuraava button should be enabled', function () {
        var isSeuraavaButtonDisabled = $('.btn-next').is(":disabled");
        expect(isSeuraavaButtonDisabled).to.be.false;
      });
    });

    // 4-fourth -click in the next-Seuraava button
    // describe('when clicking in next aka Seuraava button and select one reserved link', function() {
    //   before(function (done) {
    //     eventbus.once('roadAddressProject:fetched',function (){
    //       var ol3Feature = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadAddressProjectLayerName(), 1717275);
    //       testHelpers.selectSingleFeature(openLayersMap, ol3Feature);
    //       console.log("Ended the 4th test.");
    //       setTimeout(function(){
    //         done();
    //       },1000);
    //     });
    //     testHelpers.clickNextButton();
    //   });
    //
    //   it('Check if the project link was selected ', function(){
    //     var featureFromProjectLayer = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadAddressProjectLayerName(), 1717275);
    //     expect(featureFromProjectLayer).to.not.be.undefined;
    //     expect(featureFromProjectLayer.roadLinkData.linkId).to.be.equal(1717275);
    //   });
    // });

  });

});
