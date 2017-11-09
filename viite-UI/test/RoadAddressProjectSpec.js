/*jshint expr: true*/
define(['chai', 'eventbus', 'TestHelpers'], function(chai, eventbus, testHelpers) {
  var expect = chai.expect;
  var assert = chai.assert;

  describe('when click on the Tieosoiteprojektit button', function() {
    this.timeout(1500000);
    var openLayersMap;
    before(function(done) {
      var backend = testHelpers.fakeBackend(13, testHelpers.selectTestData('roadAddress'),354810.0, 6676460.0);

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
        $('[id^=nimi]').val('Project Two').trigger("change");
        $('[id^=alkupvm]').val('30.5.2017').trigger("change");
        $('[id^=tie]').val('1130').trigger("change");
        $('[id^=aosa]').val('4').trigger("change");
        $('[id^=losa]').val('4').trigger("change");
        eventbus.on('roadPartsValidation:checkRoadParts', function(validationResult){
          if(validationResult.success == "ok"){
            done();
          }
        });
        testHelpers.clickReserveButton();
      });

      it('Seuraava button should be enabled', function () {
        var isSeuraavaButtonDisabled = $('#generalNext').is(":disabled");
        expect(isSeuraavaButtonDisabled).to.be.false;
      });
    });

    // 4-fourth -click in the next-Seuraava button
    describe('when clicking in next aka Seuraava button and select one reserved link', function() {
      before(function () {
        eventbus.on('roadAddressProject:fetched',function (){
          var ol3Feature = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadAddressProjectLayerName(), 1717275);
          testHelpers.selectSingleFeatureByInteraction(openLayersMap, ol3Feature, testHelpers.getSingleClickNameProjectLinkLayer());
        });
        testHelpers.clickNextButton();
      });

      it('Check if the project link was selected ', function(){
        var featureFromProjectLayerNotHandled = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadAddressProjectLayerName(), 1717275);
        var featureFromProjectLayerTerminated = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadAddressProjectLayerName(), 1717361);
        var featureFromProjectLayerNotReserved = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadAddressProjectLayerName(), 499896971);
        expect(featureFromProjectLayerNotHandled).to.not.be.undefined;
        expect(featureFromProjectLayerNotHandled.projectLinkData.linkId).to.be.equal(1717275);
        expect(featureFromProjectLayerNotHandled.projectLinkData.status).to.be.equal(0);
        expect(featureFromProjectLayerTerminated).to.not.be.undefined;
        expect(featureFromProjectLayerTerminated.projectLinkData.linkId).to.be.equal(1717361);
        expect(featureFromProjectLayerTerminated.projectLinkData.status).to.be.equal(1);
        expect(featureFromProjectLayerNotReserved).to.not.be.undefined;
        expect(featureFromProjectLayerNotReserved.projectLinkData.linkId).to.be.equal(499896971);
        expect(featureFromProjectLayerNotReserved.projectLinkData.status).to.be.equal(99);
      });

      it('Check if project link data is filled', function () {

        //Check if when there is no data the inputs are empty
        var feature = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadAddressProjectLayerName(), 499897070);
        testHelpers.selectSingleFeatureByInteraction(openLayersMap, feature, testHelpers.getSingleClickNameProjectLinkLayer());
        expect(feature).to.not.be.undefined;
        expect(feature.projectLinkData.linkId).to.be.equal(499897070);
        expect($('#dropdown_0').val('New').is(':disabled')).to.be.false;
        $('#dropDown_0').val('New').change();
        expect($('#tie')).to.not.be.undefined;
        expect($('#osa')).to.not.be.undefined;
        expect($('#ajr')).to.not.be.undefined;
        console.log($('#tie'));
        var inputsEmpty = ($('#tie').val().length === 0 && $('#osa').val().length === 0 && $('#ajr').val().length === 0);
        expect(inputsEmpty).to.be.true;
        //Check if Tallenna is disabled
        expect($('.update.btn.btn-save').is(':disabled')).to.be.true;

        //Select another road to see the pop up
        feature = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadAddressProjectLayerName(), 1717275);
        testHelpers.selectSingleFeatureByInteraction(openLayersMap, feature, testHelpers.getSingleClickNameProjectLinkLayer());
        $('.yes').click();

        //Check if the values are filled in the input fields when data is valid
        feature = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadAddressProjectLayerName(), 1717395);
        testHelpers.selectSingleFeatureByInteraction(openLayersMap, feature, testHelpers.getSingleClickNameProjectLinkLayer());
        expect(feature).to.not.be.undefined;
        expect(feature.projectLinkData.linkId).to.be.equal(1717395);
        expect($('#dropdown').val('uusi').is(':disabled')).to.be.false;
        $('#dropDown').val('uusi').change();
        inputsEmpty = ($('#tie').val().length === 0 && $('#osa').val().length === 0 && $('#ajr').val().length === 0);
        expect(inputsEmpty).to.be.false;
        //Check if Tallenna is enabled
        expect($('.update.btn.btn-save').is(':disabled')).to.be.false;
      });
    });

    describe('when clicking Peruuta button', function() {
      before(function (done) {
        var ol3Feature = testHelpers.getFeatureByLinkId(openLayersMap, testHelpers.getRoadAddressProjectLayerName(), 1717275);
        testHelpers.selectSingleFeatureByInteraction(openLayersMap, ol3Feature, testHelpers.getSingleClickNameProjectLinkLayer());
        // Click Cancel (Peruuta)
        $('.cancelLink').click();
        done();
      });

      it('Check if it change to the road form', function(){
        $('.project-form button.next:visible').prop('disabled', false);
        $('.project-form button.next:visible').attr('disabled', false);
        assert($('.project-form:visible').length > 0, "Form didn't open.");
      });
    });

  });

});
