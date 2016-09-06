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

    function handleEditMessageTESTE(validateStatus) {
      var editMessageTESTE = $(
          '<div class="action-state">' +
          '  MSG de TESTE.' +
          '</div>');

      if (validateStatus) {
        editMessageTESTE.hide();
      } else {
        editMessageTESTE.show();
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
    
    eventbus.on('application:controledTR', function(show) {
      handleEditMessageTESTE(show);
    });

    $('#header').append(editMessage.hide());
  }
})(this);
