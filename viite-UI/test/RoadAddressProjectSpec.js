/*jshint expr: true*/
define(['chai', 'eventbus', 'TestHelpers'], function(chai, eventbus, testHelpers) {
  var expect = chai.expect;
  var assert = chai.assert;

  describe('when click on the Tieosoiteprojektit button', function() {
    this.timeout(1500000);
    var openLayersMap;
    before(function(done) {
      // var backend = testHelpers.fakeBackend(9, testHelpers.selectTestData('reservedProjectLinks'),533604.0, 6987750.0);
      var backend = testHelpers.fakeBackend(13, testHelpers.selectTestData('roadAddress'),354810.0, 6676460.0);//TODO add project for this zone
      // 6676776I:354812
      // var backend = testHelpers.fakeBackend(13, testHelpers.selectTestData('roadAddress'),533604.0, 6987750.0);

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
        $('.btn-reserve').prop('disabled', false);
        $('.btn-reserve').attr('disabled', false);
        testHelpers.clickReserveButton();
        console.log("Ended the 3rd test");
        done();
      });

      it('testing the presence of IT', function () {
        console.log("I needed a IT for Varaa");
      });
    });

    // 4-fourth -click in the next-Seuraava button
    describe('when clicking in next aka Seuraava button and select one reserved link', function() {
      before(function (done) {
        $('.btn-next').prop('disabled', false);
        $('.btn-next').attr('disabled', false);
        testHelpers.clickNextButton();
        var ol3Feature = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadAddressProjectLayerName(), 5172091);
        testHelpers.selectSingleFeature(openLayersMap, ol3Feature);
        setTimeout(function(){
          console.log("Ended the 4th test.");
          done();
        },1000);
        done();
      });

      // it('Check if the project link was selected ', function(){
      //   var featureFromProjectLayer = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadAddressProjectLayerName(), 5172091);
      //   expect(featureFromProjectLayer).to.not.be.undefined;
      //   expect(featureFromProjectLayer.roadLinkData.linkId).to.be.equal(5172091);
      // });
    });

    //5-fifth select reserved road link WIP
    // describe('when selecting one reserved link', function(){
    //   before(function(done){
    //     var ol3Feature = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadAddressProjectLayerName(), 5172134);
    //     testHelpers.selectSingleFeature(openLayersMap, ol3Feature);
    //     setTimeout(function(){
    //       done();
    //     },1000);
    //   });
    //
    //   it('Check if the project link was selected ', function(){
    //     var featureFromProjectLayer = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadAddressProjectLayerName(),5172091);
    //     expect(featureFromProjectLayer).to.not.be.undefined;
    //     expect(featureFromProjectLayer.roadLinkData.linkId).to.be.equal(5172091);
    //   });
    // });


    // //5-fifth select reserved road link WIP
    // describe('when selecting one reserved link', function() {
    //   before(function(done){
    //     var ol3Feature = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadAddressProjectLayerName(), 5172091);
    //     testHelpers.selectSingleFeature(openLayersMap, ol3Feature);
    //     done();
    //   });
    //
    //   it('it should shown info in the form', function() {
    //     expect($('[id^=information-content]:visible').length).to.equals(1);
    //   });
    // });

    // });
  });

});
