describe('ConfirmDialogController', function() {
    var confirmDialogShown = false;

    before(function() {
        eventbus.on('confirm:show', function() { confirmDialogShown = true; });
    });

    var resetTest = function() {
        ConfirmDialogController.initialize();
        confirmDialogShown = false;
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
            eventbus.trigger('assetPropertyValue:changed');
        });

        describe('and another asset is selected', unselectAssetAndAssertDialogShown(true));
    });
});
