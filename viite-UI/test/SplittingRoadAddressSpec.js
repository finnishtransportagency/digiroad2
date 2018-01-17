define(['chai', 'eventbus', 'TestHelpers'], function (chai, eventbus, testHelpers) {
  var expect = chai.expect;

  describe('when making a split', function () {
    this.timeout(3000000);
    var openLayersMap;
    before(function (done) {
      var backend = testHelpers.fakeBackend(13, testHelpers.selectTestData('roadAddress'), 354810.0, 6676460.0, 'projectThree');

      testHelpers.restartApplication(function (map) {
        openLayersMap = map;
        testHelpers.clickVisibleEditModeButton();
        eventbus.on('roadLayer:featuresLoaded', function () {
          testHelpers.clickProjectListButton();
          testHelpers.clickNewProjectButton();
          $('[id^=nimi]').val('projectThree').trigger("change");
          $('[id^=alkupvm]').val('30.5.2017').trigger("change");
          $('[id^=tie]').val('16081').trigger("change");
          $('[id^=aosa]').val('1').trigger("change");
          $('[id^=losa]').val('1').trigger("change");
          eventbus.on('roadPartsValidation:checkRoadParts', function (validationResult) {
            if (validationResult.success == "ok") {
              testHelpers.clickNextButton();
              done();
            }
          });
          testHelpers.clickReserveButton();
        });
      }, backend);
    });

    describe('select cut tool and make a split', function () {
      before(function (done) {
        testHelpers.selectTool('Cut');
        eventbus.trigger('map:clicked', {x: 480905.40280654473, y: 7058825.968613995});
        done();
      });

      it('check split form data', function () {
        expect( $('.cut').attr('class')).to.be.a('string', 'action cut active');
        expect($('#roadAddressProjectFormCut').html()).not.to.have.length(0);
        expect($('#dropdown_0')[0].value).to.be.a('String', 'Transfer');
        expect($('#dropdown_1')[0].value).to.be.a('String', 'New');
        expect($('#dropdown_2')[0].value).to.be.a('String', 'Terminated');
        expect($('#tie')[0].value).to.equal('16081');
        expect($('#osa')[0].value).to.equal('1');
        expect($('#trackCodeDropdown')[0].value).to.equal('0');
        expect($('#discontinuityDropdown')[0].value).to.equal('5');
        expect($('#roadTypeDropDown')[0].value).to.equal('3');
      });
    });

    describe('cancel the split', function () {
      before(function (done) {
        testHelpers.clickCancelButton();
        done();
      });

      it('verify that split form was cleared', function () {
        expect($('#projectErrors').html()).not.to.have.length(0);
      });
    });
  });
});