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
                    values: [{ propertyValue: 2 }]
                }]
            });
        });

        describe('and another asset is selected', unselectAssetAndAssertDialogShown(true));

        describe('and save button is pressed', function() {
            before(function() {
               eventbus.trigger('confirm:ok');
            });

            it('should send updated asset to backend', function() {
                assert.equal(assetSentToBackend.propertyData[0].values[0].propertyValue, "2");
            });
        });
    });
});
