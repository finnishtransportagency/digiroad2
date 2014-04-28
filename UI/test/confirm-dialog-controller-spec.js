describe('ConfirmDialogController', function() {
    var confirmDialogShown = false;

    before(function() {
        eventbus.on('confirm:show', function() { confirmDialogShown = true; });
    });

    var resetTest = function() {
        ConfirmDialogController.initialize();
        confirmDialogShown = false;
    };

    // TODO: show confirmation dialog when properties has been changed

    describe('when asset is moved', function() {
        before(function() {
            resetTest();
            eventbus.trigger('asset:moved');
        });

        describe('and another asset is selected', function() {
            before(function() {
                eventbus.trigger('asset:unselected');
            });

            it('should show confirm dialog', function() {
                assert.isTrue(confirmDialogShown);
            });
        });

        describe('and changes are saved', function() {
            before(function() {
                confirmDialogShown = false;
                eventbus.trigger('asset:saved');
            });

            describe('and another asset is selected', function() {
                before(function() {
                    eventbus.trigger('asset:unselected');
                });

                it('should not show confirm dialog', function() {
                    assert.isFalse(confirmDialogShown);
                });
            });
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

            describe('and another asset is selected', function() {
                before(function() {
                    eventbus.trigger('asset:unselected');
                });

                it('should not show confirm dialog', function() {
                    assert.isFalse(confirmDialogShown);
                });
            });
        });
    });

    describe('when asset is not moved', function () {
        before(function() {
            resetTest();
        });

        describe('and another asset is selected', function () {
            before(function () {
                eventbus.trigger('asset:unselected');
            });

            it('should not show confirm dialog', function () {
                assert.isFalse(confirmDialogShown);
            });
        });
    });
});
