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
    button.click(function() {
      executeOrShowConfirmDialog(function() {
        toggleReadOnlyMode(!applicationModel.isReadOnly());
      });
    });
    var reset = function() {
      toggleReadOnlyMode(true);
    };

    var toggleEditMode = function(mode) {
      toggleReadOnlyMode(mode);
    };

    var hasNoRolesPermission = function(userRoles) {
      return (((_.contains(userRoles, 'busStopMaintainer')) || (_.isEmpty(userRoles)) || (_.contains(userRoles, 'serviceRoadMaintainer'))) &&
      !(_.contains(userRoles, 'operator') || _.contains(userRoles, 'premium')));
    };

    return {
      element: element,
      reset: reset,
      toggleEditMode: toggleEditMode,
      hasNoRolesPermission: hasNoRolesPermission
    };
  };
}(this));