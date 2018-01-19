define(['chai', '../src/model/selectedMassTransitStop.js'], function (chai) {
  var assert = chai.assert;
  describe('SelectedMassTransitStop', function () {
    var confirmDialogShown = false;
    var assetSentToBackend = {};
    var mockBackend = {
      updateAsset: function (id, data) {
        assetSentToBackend = {
          id: id,
          data: data
        };
      }
    };
    var model = SelectedMassTransitStop.initialize(mockBackend);

    before(function () {
      eventbus = Backbone.Events;
      eventbus.on('confirm:show', function () {
        confirmDialogShown = true;
      });
    });
    after(function () {
      eventbus.stopListening();
    });

    var resetTest = function () {
      model.close();
      confirmDialogShown = false;
      assetSentToBackend = {};
      eventbus.trigger('asset:fetched', createAsset());
    };

    var assetStateIsDirty = function () {
      return function () {
        it('asset state should be dirty', function () {
          assert.equal(model.isDirty(), true);
        });
      };
    };

    var assetStateIsClean = function () {
      return function () {
        it('asset state should be clean', function () {
          assert.equal(model.isDirty(), false);
        });
      };
    };

    describe('when asset is moved', function () {
      before(function () {
        resetTest();
        model.move({
          lon: 1,
          lat: 1,
          bearing: 180,
          roadLinkId: 3
        });
      });

      describe('and another asset is selected', assetStateIsDirty());

      describe('and changes are saved', function () {
        before(function () {
          confirmDialogShown = false;
          eventbus.trigger('asset:saved');
        });

        describe('and another asset is selected', assetStateIsClean());
      });
    });

    describe('when asset is moved', function () {
      before(function () {
        resetTest();
        eventbus.trigger('asset:moved', {
          lon: 1,
          lat: 2,
          bearing: 90,
          roadLinkId: 4
        });
      });

      describe('and changes are saved', function () {
        before(function () {
          eventbus.trigger('asset:saved');
        });

        describe('and another asset is selected', assetStateIsClean());
      });
    });

    describe('when asset is not moved', function () {
      before(function () {
        resetTest();
      });

      describe('and another asset is selected', assetStateIsClean());
    });

    describe('when asset no longer falls on selected validity period', function () {
      var triggeredEvents = [];
      before(function () {
        eventbus.on('all', function (eventName) {
          triggeredEvents.push(eventName);
        });
        eventbus.trigger('validityPeriod:changed', ['future', 'past']);
      });

      it('closes asset', function () {
        assert.include(triggeredEvents, 'asset:closed');
        assert.lengthOf(triggeredEvents, 2);
      });
    });

    describe('when property value is updated', function () {
      before(function () {
        resetTest();
        model.setProperty('vaikutussuunta', [{propertyValue: '3'}]);
      });

      it('getProperties returns updated and unmodified properties', function () {
        var properties = model.getProperties();
        assert.lengthOf(properties, 2);
        assert.deepEqual(properties[0], {
          publicId: 'pysakin_tyyppi',
          propertyType: 'multiple_choice',
          required: true,
          values: [{
            propertyDisplayValue: 'Linja-autojen paikallisliikenne',
            propertyValue: '2'
          }, {
            propertyDisplayValue: 'Linja-autojen kaukoliikenne',
            propertyValue: '3'
          }
          ]
        });
        assert.deepEqual(properties[1], {
          publicId: 'vaikutussuunta',
          values: [{propertyValue: '3'}]
        });
      });

      describe('and asset is saved', function () {
        before(function () {
          model.save();
        });

        it('sends only changed properties to backend', function () {
          assert.lengthOf(assetSentToBackend.data.properties, 2);
          assert.deepEqual(assetSentToBackend.data.properties[1], {
            publicId: 'vaikutussuunta',
            values: [{propertyValue: '3'}]
          });
        });
      });
    });

    function createAsset() {
      return {
        assetTypeId: 10,
        bearing: 80,
        nationalId: 1,
        id: 300000,
        stopTypes: [2],
        lat: 6677267.45072414,
        lon: 374635.608258218,
        municipalityNumber: 235,
        propertyData: [{
          id: 0,
          localizedName: 'Vaikutussuunta',
          propertyType: 'single_choice',
          propertyValue: '2',
          publicId: 'vaikutussuunta',
          required: false,
          values: [{
            propertyDisplayValue: 'Digitointisuuntaan',
            propertyValue: '2'
          }
          ]
        }, {
          id: 200,
          localizedName: 'Pysäkin tyyppi',
          propertyType: 'multiple_choice',
          propertyValue: '<div data-publicId="pysakin_tyyppi" name="pysakin_tyyppi" class="featureattributeChoice"><input  type="checkbox" value="5"></input><label for="pysakin_tyyppi_5">Virtuaalipysäkki</label><br/><input  type="checkbox" value="1"/><label for="pysakin_tyyppi_1">Raitiovaunu</label><br/><input checked  type="checkbox" value="2"/><label for="pysakin_tyyppi_2">Linja-autojen paikallisliikenne</label><br/><input  type="checkbox" value="3"/><label for="pysakin_tyyppi_3">Linja-autojen kaukoliikenne</label><br/><input  type="checkbox" value="4"/><label for="pysakin_tyyppi_4">Linja-autojen pikavuoro</label><br/></div>',
          publicId: 'pysakin_tyyppi',
          required: true,
          values: [{
            propertyDisplayValue: 'Linja-autojen paikallisliikenne',
            propertyValue: '2'
          }, {
            propertyDisplayValue: 'Linja-autojen kaukoliikenne',
            propertyValue: '3'
          }
          ]
        }
        ],
        readOnly: true,
        roadLinkId: 1611353,
        validityDirection: 2,
        validityPeriod: 'current',
        wgslat: 60.2128746641816,
        wgslon: 24.7375812322645
      };
    }
  });
});
