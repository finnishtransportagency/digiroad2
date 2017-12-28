define(['chai', 'eventbus', 'TestHelpers'], function (chai, eventbus, testHelpers) {
  var expect = chai.expect;
  var assert = chai.assert;

  describe('when making a split', function () {
    this.timeout(1500000);
    var openLayersMap;
    before(function (done) {
      var backend = testHelpers.fakeBackend(13, testHelpers.selectTestData('roadAddress'), 354810.0, 6676460.0, 'projectThree');

      testHelpers.restartApplication(function (map) {
        openLayersMap = map;
        testHelpers.clickVisibleEditModeButton();
        eventbus.once('roadLayer:featuresLoaded', function () {
          testHelpers.clickProjectListButton();
          testHelpers.clickNewProjectButton();
          $('[id^=nimi]').val('projectThree').trigger("change");
          $('[id^=alkupvm]').val('30.5.2017').trigger("change");
          $('[id^=tie]').val('16081').trigger("change");
          $('[id^=aosa]').val('1').trigger("change");
          $('[id^=losa]').val('1').trigger("change");
          eventbus.once('roadPartsValidation:checkRoadParts', function (validationResult) {
            if (validationResult.success == "ok") {
              testHelpers.clickNextButton();
              done();
            }
          });
          testHelpers.clickReserveButton();
        });
      }, backend);
    });

    //describe('when making the split', function () {

      it('select cut tool', function () {
        eventbus.trigger('tool:changed', 'Cut');
        var cutClass = $('.cut').attr('class');
        expect(cutClass).to.be.a('string', 'action cut active');
      });

      it('click on the cut point', function () {
        eventbus.trigger('map:clicked', '{x: 480905.40280654473, y: 7058825.968613995}');
        //map:clicked {x: 480905.40280654473, y: 7058825.968613995}
      });

    //});

    // Selecionar o cut (tool:changed Cut)
  });

});