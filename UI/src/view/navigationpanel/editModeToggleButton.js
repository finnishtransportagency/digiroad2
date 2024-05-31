(function(root) {
  var executeOrShowConfirmDialog = function(action) {
    if (applicationModel.isDirty()) { new Confirm(); }
    else { action(); }
  };

  root.EditModeToggleButton = function(toolSelection) {
    var button = $('<button class="action-mode-btn btn btn-block edit-mode-btn btn-primary">').text('Siirry muokkaustilaan');
    var element = $('<div class="panel-section panel-toggle-edit-mode">').append(button);
    var toggleReadOnlyMode = function(mode) {
      applicationModel.setReadOnly(mode);
      if (mode) {
        button.removeClass('read-only-btn').addClass('edit-mode-btn');
        button.removeClass('btn-secondary').addClass('btn-primary');
        toolSelection.hide();
      } else {
        button.removeClass('edit-mode-btn').addClass('read-only-btn');
        button.removeClass('btn-primary').addClass('btn-secondary');
        toolSelection.reset();
        toolSelection.show();
      }
      button.text(mode ? 'Siirry muokkaustilaan' : 'Siirry katselutilaan');
    };
    var disableEditMode = function(mode) {
      button.prop('disabled', mode);
    };
    button.click(function() {
      executeOrShowConfirmDialog(function() {
        toggleReadOnlyMode(!applicationModel.isReadOnly());
      });
    });
    eventbus.on('zoomedOutOfEditableBoundaries', function() {
      toggleEditMode(true);
      disableEditMode(true);
    });
    eventbus.on('zoomedWithinEditableBoundaries', function() {
      disableEditMode(false);
    });
    var reset = function() {
      toggleReadOnlyMode(true);
    };

    var toggleEditMode = function(mode) {
      toggleReadOnlyMode(mode);
    };

    return {
      element: element,
      reset: reset,
      toggleEditMode: toggleEditMode
    };
  };
}(this));
