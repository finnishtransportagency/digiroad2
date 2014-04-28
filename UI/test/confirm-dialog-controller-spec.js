describe('ConfirmDialogController', function() {
    ConfirmDialogController.initialize();
    var confirmDialogShown = false;

    before(function() {
        eventbus.on('confirm:show', function() { confirmDialogShown = true; });
    });

    describe('when asset is moved', function() {
        before(function() {
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
    });
});
