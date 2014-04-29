describe('SelectedAssetController', function() {
    var confirmDialogShown = false;
    var assetSentToBackend = null;
    var mockBackend = {
        updateAsset: function(id, data) {
            assetSentToBackend = data;
        }
    };
    var controller = SelectedAssetController.initialize(mockBackend);

    before(function() {
        eventbus.on('confirm:show', function() { confirmDialogShown = true; });
    });

    var resetTest = function() {
        controller.reset();
        confirmDialogShown = false;
        assetSentToBackend = null;
        eventbus.trigger('asset:fetched', createAsset());
    };

    var unselectAssetAndAssertDialogShown = function(shown) {
        return function() {
            before(function() {
                eventbus.trigger('asset:unselected');
            });

            it('should show/hide confirm dialog correctly', function() {
                assert.equal(confirmDialogShown, shown);
            });
        };
    };

    describe('when asset is moved', function() {
        before(function() {
            resetTest();
            eventbus.trigger('asset:moved');
        });

        describe('and another asset is selected', unselectAssetAndAssertDialogShown(true));

        describe('and changes are saved', function() {
            before(function() {
                confirmDialogShown = false;
                eventbus.trigger('asset:saved');
            });

            describe('and another asset is selected', unselectAssetAndAssertDialogShown(false));
        });
    });

    describe('when asset is moved', function() {
        before(function() {
            resetTest();
            eventbus.trigger('asset:moved');
        });

        describe('and changes are saved', function() {
            before(function() {
                eventbus.trigger('asset:saved');
            });

            describe('and another asset is selected', unselectAssetAndAssertDialogShown(false));
        });
    });

    describe('when asset is not moved', function () {
        before(function() {
            resetTest();
        });

        describe('and another asset is selected', unselectAssetAndAssertDialogShown(false));
    });

    describe('when asset property is changed', function () {
        before(function () {
            resetTest();
            eventbus.trigger('assetPropertyValue:changed', {
                propertyData: [{
                    publicId: 'vaikutussuunta',
                    values: [{ propertyValue: '3' }]
                }]
            });
        });

        describe('and another asset is selected', unselectAssetAndAssertDialogShown(true));

        describe('and save button is pressed', function() {
            before(function() {
               eventbus.trigger('confirm:ok');
            });

            it('should send updated asset to backend', function() {
                assert.deepEqual(assetSentToBackend, createBackendMessagePayload());
            });
        });
    });

    function createAsset() {
        return {
            assetTypeId: 10,
            bearing: 80,
            externalId: 1,
            id: 300000,
            imageIds: ['2_1398341376263'],
            lat: 6677267.45072414,
            lon: 374635.608258218,
            municipalityNumber: 235,
            propertyData: [{
                id: 0,
                localizedName: 'Vaikutussuunta',
                propertyType: 'single_choice',
                propertyUiIndex: 65,
                propertyValue: '2',
                publicId: 'vaikutussuunta',
                required: false,
                values: [{
                    imageId: null,
                    propertyDisplayValue: 'Digitointisuuntaan',
                    propertyValue: '2'}
                ]}, {
                id: 200,
                localizedName: 'Pysäkin tyyppi',
                propertyType: 'multiple_choice',
                propertyUiIndex: 90,
                propertyValue: '<div data-publicId="pysakin_tyyppi" name="pysakin_tyyppi" class="featureattributeChoice"><input  type="checkbox" value="5"></input><label for="pysakin_tyyppi_5">Virtuaalipysäkki</label><br/><input  type="checkbox" value="1"></input><label for="pysakin_tyyppi_1">Raitiovaunu</label><br/><input checked  type="checkbox" value="2"></input><label for="pysakin_tyyppi_2">Linja-autojen paikallisliikenne</label><br/><input  type="checkbox" value="3"></input><label for="pysakin_tyyppi_3">Linja-autojen kaukoliikenne</label><br/><input  type="checkbox" value="4"></input><label for="pysakin_tyyppi_4">Linja-autojen pikavuoro</label><br/></div>',
                publicId: 'pysakin_tyyppi',
                required: true,
                values: [{
                    imageId: '2_1398341376263',
                    propertyDisplayValue: 'Linja-autojen paikallisliikenne',
                    propertyValue: '2'
                }, {
                    imageId: '3_1398341376270',
                    propertyDisplayValue: 'Linja-autojen kaukoliikenne',
                    propertyValue: '3' }
                ]}
            ],
            readOnly: true,
            roadLinkId: 1140018963,
            validityDirection: 2,
            validityPeriod: 'current',
            wgslat: 60.2128746641816,
            wgslon: 24.7375812322645
        };
    }

    function createBackendMessagePayload() {
        return {
            assetTypeId: 10,
            bearing: 80,
            lat: 6677267.45072414,
            lon: 374635.608258218,
            roadLinkId: 1140018963,
            properties: [{
                publicId: 'vaikutussuunta',
                values: [{
                    propertyValue: '3',
                    propertyDisplayValue: 'vaikutussuunta'
                }]
            }, {
                publicId: 'pysakin_tyyppi',
                values: [{
                    propertyValue: '2',
                    propertyDisplayValue: 'pysakin_tyyppi'
                }, {
                    propertyValue: '3',
                    propertyDisplayValue: 'pysakin_tyyppi'
                }]
            }]
        };
    }
});
