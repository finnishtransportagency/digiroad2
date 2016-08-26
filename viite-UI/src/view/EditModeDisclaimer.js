(function(root) {
  root.EditModeDisclaimer = {
    initialize: initialize
  };

  function initialize(instructionsPopup) {
    var editMessage = $(
      '<div class="action-state">' +
      '  Olet muokkaustilassa. Kuntakäyttäjien tulee kohdistaa muutokset katuverkolle, ELY-käyttäjien maantieverkolle.' +
      '</div>');

    function handleEditMessage(readOnly) {
      if (readOnly) {
        editMessage.hide();
      } else {
        editMessage.show();
      }
    }

    function showEditInstructionsPopup(readOnly) {
      if(!readOnly) {
        instructionsPopup.show('Kuntakäyttäjien tulee kohdistaa muutokset katuverkolle, ELY-käyttäjien maantieverkolle.', 4000);
      }
    }

    eventbus.on('application:readOnly', function(readOnly) {
      handleEditMessage(readOnly);
      showEditInstructionsPopup(readOnly);
    });

    $('#header').append(editMessage.hide());
  }
})(this);
