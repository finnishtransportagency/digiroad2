/*jshint expr: true*/
define(['chai', 'eventbus', 'TestHelpers'], function(chai, eventbus, testHelpers) {
  var expect = chai.expect;
  var assert = chai.assert;

  describe('when click on the Tieosoiteprojektit button', function() {
    this.timeout(1500000);
    before(function(done) {
      var backend = testHelpers.fakeBackend(13, testHelpers.selectTestData('roadAddress'),354810.0, 6676460.0);

      testHelpers.restartApplication(function(map) {
        eventbus.once('roadLayer:featuresLoaded', function() {
          done();
        });
      }, backend);
    });

    //1-first -open project list
    before(function(done) {
      $('[id^=projectListButton]:visible').prop('disabled', false);
      $('[id^=projectListButton]:visible').attr('disabled', false);
      testHelpers.clickProjectListButton();
      done();
    });

    it('open project list window', function () {
      $('[id^=projectListButton]').prop('disabled', false);
      $('[id^=projectListButton]').attr('disabled', false);
      assert($('[id^=project-window]:visible').length > 0, "Windows didn't open. Check permissions.");
    });
  });

    //2-second -click Avaa button and display form info
    describe('when clicking in new project button', function() {
      before(function(done) {
        $('[id*="open-project"]:visible').prop('disabled', false);
        $('[id*="open-project"]:visible').attr('disabled', false);
        testHelpers.clickNewProjectButton();
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
        done();
      });
    });

  // 4-fourth -click in the next-Seuraava button WIP
  //   describe('when clicking in next aka Seuraava button', function() {
  //     before(function (done) {
  //       $('.btn-next').prop('disabled', false);
  //       $('.btn-next').attr('disabled', false);
  //       testHelpers.clickNextButton();
  //
  //       done();
  //     });
  //   });

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
